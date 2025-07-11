package com.hoprxi.infrastructure.persistence;

import com.hoprxi.application.AreaBatchImport;
import com.hoprxi.infrastructure.DecryptUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.poi.ss.usermodel.*;
import org.elasticsearch.client.*;
import salt.hoprxi.to.PinYin;

import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-09
 */
public class ESAreaBatchImport implements AreaBatchImport {
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClientBuilder BUILDER;

    static {
        Config config = ConfigFactory.load("area");
        Config read = config.getConfigList("read").get(0);
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DecryptUtil.decrypt(entry, read.getString("user"));
        String password = DecryptUtil.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))).addHeader(HttpHeaders.CONTENT_TYPE, "application/x-ndjson;charset=utf-8");
        //builder.setHttpAsyncResponseConsumerFactory(
        //new HttpAsyncResponseConsumerFactory
        //.HeapBufferedResponseConsumerFactory(30 * 1024 * 1024 * 1024));
        COMMON_OPTIONS = builder.build();
        BUILDER = RestClient.builder(new HttpHost(host, port, "https"));
    }


    @Override
    public void importXlsFrom(InputStream is) throws IOException {
        Workbook workbook = WorkbookFactory.create(is);
        Sheet sheet = workbook.getSheetAt(0);
        StringBuilder batch = new StringBuilder();
        for (int i = 1, j = sheet.getLastRowNum() + 1; i < j; i++) {
            Row row = sheet.getRow(i);
            batch.append(parseBulk(row));
            if (i % 4096 == 0 || i == j - 1) {
                try (RestClient client = BUILDER.build()) {
                    Request request = new Request("POST", "/_bulk?refresh=wait_for&pretty&filter_path=items.*.error");
                    request.setOptions(COMMON_OPTIONS);
                    request.setJsonEntity(batch.toString());
                    Response response = client.performRequest(request);
                    System.out.println(response.getEntity().getContentLength());
                }
                batch.setLength(0);
            }
        }
    }

    private StringBuilder parseBulk(Row row) {
        int divisor = row.getPhysicalNumberOfCells();
        String name = null, abbreviation = null;
        double longitude = 0.0, latitude = 0.0;
        int code = -1, parentCode = -1, level = 0, sort = 0;
        for (int k = row.getFirstCellNum(); k < row.getLastCellNum(); k++) {
            Cell cell = row.getCell(k);
            switch (k % divisor) {
                case 0:
                    code = Double.valueOf(cell.getNumericCellValue()).intValue();
                    break;
                case 1:
                    name = cell.getStringCellValue();
                    break;
                case 2:
                    parentCode = Double.valueOf(cell.getNumericCellValue()).intValue();
                    break;
                case 3:
                    abbreviation = cell.getStringCellValue();
                    break;
                case 4:
                    longitude = cell.getNumericCellValue();
                    break;
                case 5:
                    latitude = cell.getNumericCellValue();
                    break;
                case 6:
                    level = (int) cell.getNumericCellValue();
                    break;
            }
        }
        /*
        switch (level) {
            case 0:
                cellJoiner.add("'COUNTRY'");
                break;
            case 1:
                cellJoiner.add("'PROVINCE'");
                break;
            case 2:
                cellJoiner.add("'CITY'");
                break;
            case 3:
                cellJoiner.add("'COUNTY'");
                break;
            case 4:
                cellJoiner.add("'TOWN'");
                break;
        }
         */
        StringBuilder sb = new StringBuilder("{\"index\":{\"_index\":\"area\",\"_id\":");
        sb.append(code).append("}}\n");
        sb.append("{\"code\":").append(code).append(",\"parent_code\":").append(parentCode).append(",\"name\":{")
                .append("\"name\":\"").append(name).append("\",\"initials\":").append((int) PinYin.toShortPinYing(name).charAt(0))
                .append(",\"abbreviation\":\"").append(abbreviation).append("\",\"mnemonic\":\"")
                .append(PinYin.toShortPinYing(abbreviation)).append("\"},\"zipcode\":\"").append("\",\"telephone_code\":\"")
                .append("\",\"location\": {\"lat\":").append(latitude).append(",\"lon\":").append(longitude)
                .append("},\"type\":\"");
        switch (level) {
            case 0:
                sb.append("COUNTRY");
                break;
            case 1:
                sb.append("PROVINCE");
                break;
            case 2:
                sb.append("CITY");
                break;
            case 3:
                sb.append("COUNTY");
                break;
            case 4:
                sb.append("TOWN");
                break;
        }
        sb.append("\"}\n");
        return sb;
    }


    private String readCellValue(Cell cell) {
        if (cell == null || cell.toString().trim().isEmpty()) {
            return null;
        }
        String result = null;
        switch (cell.getCellType()) {
            case NUMERIC:   //数字
                if (DateUtil.isCellDateFormatted(cell)) {//注意：DateUtil.isCellDateFormatted()方法对“2019年1月18日"这种格式的日期，判断会出现问题，需要另行处理
                    DateTimeFormatter dtf;
                    SimpleDateFormat sdf;
                    short format = cell.getCellStyle().getDataFormat();
                    if (format == 20 || format == 32) {
                        sdf = new SimpleDateFormat("HH:mm");
                    } else if (format == 14 || format == 31 || format == 57 || format == 58) {
                        // 处理自定义日期格式：m月d日(通过判断单元格的格式id解决，id的值是58)
                        sdf = new SimpleDateFormat("yyyy-MM-dd");
                        double value = cell.getNumericCellValue();
                        Date date = DateUtil.getJavaDate(value);
                        result = sdf.format(date);
                    } else {// 日期
                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    }
                    try {
                        result = sdf.format(cell.getDateCellValue());// 日期
                    } catch (Exception e) {
                        try {
                            throw new Exception("exception on get date data !".concat(e.toString()));
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                } else {
                    NumberFormat nf = NumberFormat.getNumberInstance();
                    nf.setMaximumFractionDigits(3);
                    nf.setRoundingMode(RoundingMode.HALF_EVEN);
                    nf.setGroupingUsed(false);//去除,分割符
                    result = nf.format(cell.getNumericCellValue());
                    /*
                    BigDecimal bd = new BigDecimal(cell.getNumericCellValue());
                    bd.setScale(3, RoundingMode.HALF_UP);
                    returnValue = bd.toPlainString();
                     */
                }
                break;
            case STRING:    //字符串
                result = cell.getStringCellValue().trim();
                break;
            case BOOLEAN:   //布尔
                Boolean booleanValue = cell.getBooleanCellValue();
                result = booleanValue.toString();
                break;
            case BLANK:     // 空值
                break;
            case FORMULA:   // 公式
                result = cell.getCellFormula();
                break;
            case ERROR:     // 故障
                break;
            default:
                break;
        }
        return result;
    }
}
