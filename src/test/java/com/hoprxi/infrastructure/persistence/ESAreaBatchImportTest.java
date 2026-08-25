package com.hoprxi.infrastructure.persistence;

import com.hoprxi.application.AreaBatchImport;
import org.testng.annotations.Test;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Objects;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-11
 */
public class ESAreaBatchImportTest {
    static {
        //StoreKeyLoad.loadSecretKey("keystore.jks", "Qwe123465", "slave.tooo.top:9200");
        StoreKeyLoad.loadSecretKey("keystore.jks", "Qwe123465",
                new String[]{"slave.tooo.top:9200", "slave.tooo.top:9201"});
    }

    @Test
    public void testImportFromXls() throws IOException, SQLException {
        final AreaBatchImport areaBatchImport = new ESAreaBatchImport();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL url = loader.getResource("areas.xls");
        areaBatchImport.importFromXls(Objects.requireNonNull(url).openStream());
    }
}