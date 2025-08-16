package com.hoprxi.rest;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.hoprxi.infrastructure.query.ESAreaQuery;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.stream.StreamMessage;
import com.linecorp.armeria.common.stream.StreamWriter;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import com.linecorp.armeria.server.annotation.PathPrefix;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;

import java.io.*;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/13
 */
@PathPrefix("/v1")
public class AreaSevice {
    private final ESAreaQuery query = new ESAreaQuery();
    private static final int BUFFER_SIZE = 8192; // 8KB缓冲区
    private static final PooledByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;
    private final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    @Get("/areas/{code}")
    public HttpResponse area(ServiceRequestContext ctx, @Param("code") int code) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        stream.write(ResponseHeaders.of(HttpStatus.OK,
                HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
        ctx.blockingTaskExecutor().execute(() -> {
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                copyRaw(gen, query.query(code));
                gen.flush();
                stream.write(HttpData.wrap(buffer));

            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                stream.close();
            }
        });
        return HttpResponse.of(stream);
    }

    @Get("/areas/{code}/juri")
    public HttpResponse jrui(ServiceRequestContext ctx, @Param("code") int code) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        stream.write(ResponseHeaders.of(HttpStatus.OK,
                HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
        ctx.blockingTaskExecutor().execute(() -> {
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                copyRaw(gen, query.queryJurisdiction(code));
                gen.flush();
                stream.write(HttpData.wrap(buffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                stream.close();
            }

        });
        return HttpResponse.of(stream);
    }

    private void copyRaw(JsonGenerator generator, OutputStream source) throws IOException {
        InputStream is = new ByteArrayInputStream(((ByteArrayOutputStream) source).toByteArray());
        JsonParser parser = JSON_FACTORY.createParser(is);
        while (parser.nextToken() != null) {
            generator.copyCurrentEvent(parser);
        }
    }
}
