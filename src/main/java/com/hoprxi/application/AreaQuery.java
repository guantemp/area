package com.hoprxi.application;

import com.hoprxi.infrastructure.query.ESAreaQuery;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025-09-04
 */

public interface AreaQuery {
    /**
     * Returns an InputStream containing the JSON of the '_source' field from Elasticsearch,
     * or null if the document does not exist or has no _source.
     * The returned InputStream MUST be closed by the caller to avoid memory leaks.
     */
    InputStream query(int code);

    OutputStream queryCountry();

    OutputStream query(String key, EnumSet<ESAreaQuery.Level> filters, int from, int size);

    OutputStream query(EnumSet<ESAreaQuery.Level> filters, String searchAfter, int size);

    OutputStream queryJurisdiction(int code);
}
