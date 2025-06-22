package com.hoprxi.infrastructure;

import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public class PsqlSetupTest {

    @Test
    public void testSetup() throws SQLException, IOException, URISyntaxException {
        PsqlSetup.setup();
    }
}