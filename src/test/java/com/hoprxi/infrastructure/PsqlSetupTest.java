package com.hoprxi.infrastructure;

import org.testng.annotations.Test;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public class PsqlSetupTest {
    static {
        StoreKeyLoad.loadSecretKey("keystore.jks", "Qwe123465",
                new String[]{"129.28.29.105:6543:P$Qwe123465Pg", "129.28.29.105:5432:P$Qwe123465Pg"});
    }

    @Test
    public void testSetup() throws SQLException, IOException, URISyntaxException {
        PsqlSetup.setup();
    }
}