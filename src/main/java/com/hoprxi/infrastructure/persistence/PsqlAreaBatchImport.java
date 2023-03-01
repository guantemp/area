package com.hoprxi.infrastructure.persistence;

import com.hoprxi.application.AreaBatchImport;
import com.hoprxi.domain.model.Name;
import com.hoprxi.domain.model.coordinate.Boundary;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.PsqlAreaUtil;
import com.hoprxi.infrastructure.PsqlUtil;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public class PsqlAreaBatchImport implements AreaBatchImport {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsqlAreaBatchImport.class);

    @Override
    public void importXlsFrom(InputStream is) throws IOException, SQLException {
        Workbook workbook = WorkbookFactory.create(is);
        Sheet sheet = workbook.getSheetAt(0);
        try (Connection connection = PsqlUtil.getConnection()) {
            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            StringJoiner sql = new StringJoiner(",", "insert into area (code,parent_code,name,boundary,\"type\") values", "");
            for (int i = 1, j = sheet.getLastRowNum() + 1; i < j; i++) {
                Row row = sheet.getRow(i);
                StringJoiner values = extracted(row);
                sql.add(values.toString());
                if (i % 513 == 0) {
                    statement.addBatch(sql.toString());
                    sql = new StringJoiner(",", "insert into area (code,parent_code,name,boundary,\"type\") values", "");
                }
                if (i == j - 1) {
                    statement.addBatch(sql.toString());
                }
                if (i % 12289 == 0) {
                    statement.executeBatch();
                    connection.commit();
                    connection.setAutoCommit(true);
                    connection.setAutoCommit(false);
                    statement = connection.createStatement();
                }
                if (i == j - 1) {
                    statement.executeBatch();
                    connection.commit();
                    connection.setAutoCommit(true);
                }
            }
        }
        workbook.close();
    }

    private StringJoiner extracted(Row row) {
        StringJoiner cellJoiner = new StringJoiner(",", "(", ")");
        int divisor = row.getPhysicalNumberOfCells();
        String name = null;
        double longitude = 0.0, latitude = 0.0;
        for (int k = row.getFirstCellNum(); k < row.getLastCellNum(); k++) {
            Cell cell = row.getCell(k);
            switch (k % divisor) {
                case 0:
                case 2:
                    cell.setCellType(CellType.STRING);
                    String code = cell.getStringCellValue();
                    cellJoiner.add(code);
                    break;
                case 1:
                    name = cell.getStringCellValue();
                    break;
                case 3:
                    cellJoiner.add("'" + PsqlAreaUtil.toJson(new Name(name, cell.getStringCellValue())) + "'");
                    break;
                case 4:
                    longitude = cell.getNumericCellValue();
                    break;
                case 5:
                    latitude = cell.getNumericCellValue();
                    cellJoiner.add("'" + PsqlAreaUtil.toJson(new Boundary(new WGS84(longitude, latitude))) + "'");
                    break;
                case 6:
                    int level = (int) cell.getNumericCellValue();
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
                    break;
            }
        }
        return cellJoiner;
    }

    private String getCellValueByCell(Cell cell) {
        if (cell == null || cell.toString().trim().isEmpty()) {
            return "";
        }
        String cellValue = "";
        CellType cellType = cell.getCellType();
        switch (cellType) {
            case NUMERIC:// 把枚举常量前的冗余类信息去掉编译即可通过
                short format = cell.getCellStyle().getDataFormat();
                if (DateUtil.isCellDateFormatted(cell)) {//注意：DateUtil.isCellDateFormatted()方法对“2019年1月18日"这种格式的日期，判断会出现问题，需要另行处理
                    SimpleDateFormat sdf = null;
                    if (format == 20 || format == 32) {
                        sdf = new SimpleDateFormat("HH:mm");
                    } else if (format == 14 || format == 31 || format == 57 || format == 58) {
                        // 处理自定义日期格式：m月d日(通过判断单元格的格式id解决，id的值是58)
                        sdf = new SimpleDateFormat("yyyy-MM-dd");
                        double value = cell.getNumericCellValue();
                        Date date = org.apache.poi.ss.usermodel.DateUtil.getJavaDate(value);
                        cellValue = sdf.format(date);
                    } else {// 日期
                        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    }
                    try {
                        cellValue = sdf.format(cell.getDateCellValue());// 日期
                    } catch (Exception e) {
                        try {
                            throw new Exception("exception on get date data !".concat(e.toString()));
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    } finally {
                    }
                } else {
                    cellValue = String.valueOf(cell.getNumericCellValue());
                    //BigDecimal bd = new BigDecimal(cell.getNumericCellValue());
                    //cellValue = bd.toPlainString();// 数值 这种用BigDecimal包装再获取plainString，可以防止获取到科学计数值
                }
                break;
            case STRING: // 字符串
                cellValue = cell.getStringCellValue();
                break;
            case BOOLEAN: // Boolean
                cellValue = cell.getBooleanCellValue() + "";
                break;
            case FORMULA: // 公式
            {
//            cellValue = cell.getCellFormula();//读取单元格中的公式
                cellValue = String.valueOf(cell.getNumericCellValue());//读取单元格中的数值
            }
            break;
            case BLANK: // 空值
                cellValue = "";
                break;
            case ERROR: // 故障
                cellValue = "ERROR VALUE";
                break;
            default:
                cellValue = "UNKNOW VALUE";
                break;
        }
        return cellValue;
    }
}