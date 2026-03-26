/*
 * Copyright (c) 2025. www.hoprxi.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.hoprxi.infrastructure.persistence;

import com.hoprxi.application.AreaBatchImport;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.poi.ss.usermodel.*;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import salt.hoprxi.crypto.application.DatabaseSpecDecrypt;
import salt.hoprxi.to.PinYin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.2 builder 2025-07-09
 */
public class ESAreaBatchImport implements AreaBatchImport {
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClient CLIENT;
    private static final ThreadLocal<Request> REQUEST_POOL;

    static {
        Config config = ConfigFactory.load("area").resolve();
        Config read = config.getConfigList("databases").getFirst();
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DatabaseSpecDecrypt.decrypt(entry, read.getString("user"));
        String password = DatabaseSpecDecrypt.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))).addHeader(HttpHeaders.CONTENT_TYPE, "application/x-ndjson;charset=utf-8");
        COMMON_OPTIONS = builder.build();
        /*
        // 1. 创建信任所有证书的策略
        TrustStrategy trustStrategy = new TrustStrategy() {
            @Override
            public boolean isTrusted(X509Certificate[] chain, String authType) {
                return true; // 信任所有证书，无论是否有效、过期或自签名
            }
        };
        SSLContext sslContext;
        // 2. 构建忽略证书验证的 SSLContext
        try {
           sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, trustStrategy) // null 表示使用默认的 KeyStore
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "https"))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // 设置自定义的 SSLContext
                    httpClientBuilder.setSSLContext(sslContext);
                    // 禁用主机名验证 (强烈建议同时禁用)
                    httpClientBuilder.setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    // 可以在此添加其他 HttpClient 配置（如超时、认证等）
                    return httpClientBuilder;
                });
        CLIENT = builder.build();
         */
        CLIENT = RestClient.builder(new HttpHost(host, port, "https")).build();
        REQUEST_POOL = ThreadLocal.withInitial(
                () -> new Request("POST", "/area/_bulk")
        );
    }

    @Override
    public void importFromXls(InputStream is) throws IOException {
        final int TOTAL_COLUMNS = 7; // code, name, parentCode, abbreviation, longitude, latitude, level
        try (Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            StringBuilder batch = new StringBuilder(4096);//4kb
            for (int i = 1, j = sheet.getLastRowNum() + 1; i < j; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                StringBuilder bulkLine = parseBulk(row, TOTAL_COLUMNS);
                if (bulkLine != null) {
                    batch.append(bulkLine);
                }
                if (i % 2048 == 0 || i == j - 1) {
                    if (batch.length() > 0) { //每2048条或最后不足2048条，
                        Request request = REQUEST_POOL.get();//?refresh=wait_for&pretty&filter_path=items.*.error
                        request.setOptions(COMMON_OPTIONS);
                        request.setJsonEntity(batch.toString());
                        //System.out.println(batch.length());
                        //System.out.println(batch);
                        CLIENT.performRequest(request);
                    }
                    batch.setLength(0);
                }
            }
        }
    }

    private StringBuilder parseBulk(Row row, int expectedColumns) {
        /*
        int divisor = row.getPhysicalNumberOfCells();
        String name = null, abbreviation = null;
        double longitude = 0.0, latitude = 0.0;
        int code = -1, parentCode = -1, level = 0;
        for (int k = row.getFirstCellNum(); k < row.getLastCellNum(); k++) {
            Cell cell = row.getCell(k);
            switch (k % divisor) {
                case 0 -> code = (int) cell.getNumericCellValue();
                case 1 -> name = cell.getStringCellValue();
                case 2 -> parentCode = (int) cell.getNumericCellValue();
                case 3 -> abbreviation = cell.getStringCellValue();
                case 4 -> longitude = cell.getNumericCellValue();
                case 5 -> latitude = cell.getNumericCellValue();
                case 6 -> level = (int) cell.getNumericCellValue();
            }
        }

         */
        // 辅助方法：安全获取单元格值
        Object[] values = new Object[expectedColumns];
        boolean valid = true;

        for (int k = 0; k < expectedColumns; k++) {
            Cell cell = row.getCell(k);
            if (cell == null) {
                values[k] = null;
                continue;
            }

            try {
                switch (cell.getCellType()) {
                    case STRING:
                        values[k] = cell.getStringCellValue();
                        break;
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            values[k] = null; // 不支持日期
                            valid = false;
                        } else {
                            double num = cell.getNumericCellValue();
                            // 判断是否为整数（如 code、level）
                            if (k == 0 || k == 2 || k == 6) { // code, parentCode, level
                                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                                    values[k] = (int) num;
                                } else {
                                    valid = false;
                                }
                            } else {
                                values[k] = num; // longitude, latitude
                            }
                        }
                        break;
                    case BLANK:
                    case _NONE:
                        values[k] = null;
                        break;
                    default:
                        valid = false;
                }
            } catch (Exception e) {
                valid = false;
            }
        }

        if (!valid) {
            // 可选：记录日志
            return null;
        }

        Integer code = (Integer) values[0];
        String name = (String) values[1];
        Integer parentCode = (Integer) values[2];
        String abbreviation = (String) values[3];
        Double longitude = (Double) values[4];
        Double latitude = (Double) values[5];
        Integer level = (Integer) values[6];

        // 必填字段校验
        if (code == null || name == null || parentCode == null || level == null ||
            longitude == null || latitude == null || abbreviation == null) {
            return null; // 跳过无效行
        }
        // 转义字符串（防止 JSON 注入）
        String escapedName = escapeJson(name);
        String escapedAbbr = escapeJson(abbreviation);

        StringBuilder sb = new StringBuilder(512).append("{\"index\":{\"_id\":");
        sb.append(code).append("}}\n");
        sb.append("{\"code\":").append(code)
                .append(",\"parent_code\":").append(parentCode)
                .append(",\"name\":{")
                .append("\"name\":\"").append(escapedName)
                .append("\",\"abbreviation\":\"").append(escapedAbbr)
                .append("\"},\"zipcode\":\"\",\"telephone_code\":\"\"")
                .append(",\"location\":{\"lat\":").append( latitude)//小数6位，精度约为 10 厘米
                .append(",\"lon\":").append(longitude)
                .append("},\"level\":");

        // level 映射
        switch (level) {
            case 0 -> sb.append("{\"name\":\"COUNTRY\",\"order\":0}");
            case 1 -> sb.append("{\"name\":\"PROVINCE\",\"order\":1}");
            case 2 -> sb.append("{\"name\":\"CITY\",\"order\":2}");
            case 3 -> sb.append("{\"name\":\"COUNTY\",\"order\":3}");
            case 4 -> sb.append("{\"name\":\"TOWN\",\"order\":4}");
            default -> {
                return null; // 无效 level
            }
        }

        sb.append("}\n");
        return sb;
    }

    // 简单 JSON 字符串转义（生产环境建议用 Jackson 或 Gson）
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
