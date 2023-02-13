package com.hoprxi.application;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public interface AreaBatchImport {
    void importXlsFrom(InputStream is) throws IOException, SQLException;
}
