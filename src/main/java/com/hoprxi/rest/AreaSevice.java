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
import java.util.EnumSet;
import java.util.Optional;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/13
 */
@PathPrefix("/v1")
public class AreaSevice {
    private static final int OFFSET = 0;
    private static final int SIZE = 64;
    private final ESAreaQuery query = new ESAreaQuery();
    private static final int BUFFER_SIZE = 4096; // 4KB缓冲区
    private static final PooledByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;
    private final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    @Get("/areas/{code}")
    public HttpResponse area(ServiceRequestContext ctx, @Param("code") int code) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.blockingTaskExecutor().execute(() -> {
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                OutputStream source = query.query(code);
                copyRaw(gen, source);
                gen.flush();
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
            } catch (IOException e) {
                handleStreamError(stream, e);
            } finally {
                stream.close();
            }
        });
        return HttpResponse.of(stream);
    }

    @Get("/areas/{code}/juri")
    public HttpResponse jrui(ServiceRequestContext ctx, @Param("code") int code) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.blockingTaskExecutor().execute(() -> {
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                copyRaw(gen, query.queryJurisdiction(code));
                gen.close();
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                stream.close();
            }
        });
        return HttpResponse.of(stream);
    }

    @Get("/areas")
    public HttpResponse query(ServiceRequestContext ctx) {
        QueryParams params = ctx.queryParams();
        int offset = params.getInt("offset", OFFSET);
        int size = params.getInt("size", SIZE);
        EnumSet<ESAreaQuery.Level> sets = EnumSet.noneOf(ESAreaQuery.Level.class);
        Optional.ofNullable(params.get("filter")).filter(f -> !f.isBlank()).ifPresent(f -> {
            String[] temps = f.split(",");
            for (String temp : temps) {
                ESAreaQuery.Level level = ESAreaQuery.Level.of(temp);
                if (level != null)
                    sets.add(level);
            }
        });
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        Optional.ofNullable(params.get("q")).filter(q -> !q.isBlank()).ifPresentOrElse(q -> {//key 查询
            ctx.blockingTaskExecutor().execute(() -> {
                try {
                    ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                    OutputStream os = new ByteBufOutputStream(buffer);
                    JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                    copyRaw(gen, this.query.query(q, sets, offset, size));
                    gen.close();
                    stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                    stream.write(HttpData.wrap(buffer));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    stream.close();
                }
            });
        }, () -> {//全局查询
            String searchAfter = params.get("searchAfter", "");
            ctx.blockingTaskExecutor().execute(() -> {
                try {
                    ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                    OutputStream os = new ByteBufOutputStream(buffer);
                    JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                    copyRaw(gen, this.query.query(sets, searchAfter, size));
                    gen.close();
                    stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                    stream.write(HttpData.wrap(buffer));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    stream.close();
                }
            });
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

    private void handleStreamError(StreamWriter<HttpObject> stream, IOException e) {
        ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
        OutputStream os = new ByteBufOutputStream(buffer);
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(os);) {
            gen.writeStartObject();
            gen.writeStringField("error", "Stream generation failed");
            gen.writeStringField("message", e.getMessage());
            gen.writeEndObject();
            gen.close();
            stream.write(HttpData.wrap(buffer));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
