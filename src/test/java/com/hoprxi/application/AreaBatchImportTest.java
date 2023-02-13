package com.hoprxi.application;

import com.hoprxi.infrastructure.persistence.PsqlAreaBatchImport;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public class AreaBatchImportTest {

    @Test
    public void testImportXlsFrom() throws IOException, SQLException {
        final AreaBatchImport areaBatchImport = new PsqlAreaBatchImport();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url=loader.getResource("areas.xls");
        areaBatchImport.importXlsFrom(url.openStream());
        //System.out.println(loader.getResource("areas.xls").getFile());
    }
}