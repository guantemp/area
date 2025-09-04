package com.hoprxi.application;

import com.hoprxi.infrastructure.query.ESAreaQuery;

import java.io.OutputStream;
import java.util.EnumSet;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025-09-04
 */

public interface AreaQuery {
    OutputStream query(int code);

    OutputStream queryCountry();

    OutputStream query(String key, EnumSet<ESAreaQuery.Level> filters, int from, int size);

    OutputStream query(EnumSet<ESAreaQuery.Level> filters, String searchAfter, int size);

    OutputStream queryJurisdiction(int code);
}
