package com.hoprxi.application;

import com.hoprxi.infrastructure.query.ESAreaQuery;
import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.EnumSet;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025-09-04
 */

public interface AreaQuery {
    /**
     * Returns a {@link ByteBuf} that wraps the _source content (with _meta removed).
     * <p>
     * ⚠️ CALLER MUST CLOSE THE RETURNED InputStream!
     * Failure to do so will cause direct memory leak in Netty's pool.
     * </p>
     */
    InputStream find(int code);

    InputStream queryCountry();

    InputStream query(String key, EnumSet<ESAreaQuery.Level> filters, int from, int size);

    InputStream query(EnumSet<ESAreaQuery.Level> filters, String searchAfter, int size);

    InputStream queryJurisdiction(int code);
}
