package com.hoprxi.infrastructure.persistence;

import com.hoprxi.application.AreaBatchImport;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.poi.ss.usermodel.*;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import salt.hoprxi.crypto.application.DatabaseSpecDecrypt;
import salt.hoprxi.to.PinYin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-09
 */
public class ESAreaBatchImport implements AreaBatchImport {
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClient CLIENT;
    private static final ThreadLocal<Request> REQUEST_POOL;

    static {
        Config config = ConfigFactory.load("area");
        Config read = config.getConfigList("read").getFirst();
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DatabaseSpecDecrypt.decrypt(entry, read.getString("user"));
        String password = DatabaseSpecDecrypt.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))).addHeader(HttpHeaders.CONTENT_TYPE, "application/x-ndjson;charset=utf-8");
        //builder.setHttpAsyncResponseConsumerFactory(
        //new HttpAsyncResponseConsumerFactory
        //.HeapBufferedResponseConsumerFactory(30 * 1024 * 1024 * 1024));
        COMMON_OPTIONS = builder.build();
        CLIENT = RestClient.builder(new HttpHost(host, port, "https")).build();
        REQUEST_POOL = ThreadLocal.withInitial(
                () -> new Request("POST", "/area/_bulk")
        );
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
                Request request = REQUEST_POOL.get();//?refresh=wait_for&pretty&filter_path=items.*.error
                request.setOptions(COMMON_OPTIONS);
                request.setJsonEntity(batch.toString());
                //request.getParameters().clear();
                System.out.println(batch);
                Response response = CLIENT.performRequest(request);
                //System.out.println(response.getEntity().getContentLength());
                batch.setLength(0);
            }
        }
    }

    private StringBuilder parseBulk(Row row) {
        int divisor = row.getPhysicalNumberOfCells();
        String name = null, abbreviation = null;
        double longitude = 0.0, latitude = 0.0;
        int code = -1, parentCode = -1, level = 0;
        for (int k = row.getFirstCellNum(); k < row.getLastCellNum(); k++) {
            Cell cell = row.getCell(k);
            switch (k % divisor) {
                case 0:
                    //System.out.println(cell.getCellType());
                    code = (int) cell.getNumericCellValue();
                    break;
                case 1:
                    name = cell.getStringCellValue();
                    break;
                case 2:
                    parentCode = (int) cell.getNumericCellValue();
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
        StringBuilder sb = new StringBuilder("{\"index\":{\"_id\":");
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
}
