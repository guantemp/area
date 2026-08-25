/*
 * Copyright (c) 2026. www.hoprxi.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.hoprxi.infrastructure.query;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.hoprxi.application.AreaQuery;
import com.hoprxi.application.AreaSearchException;
import com.hoprxi.application.NotFoundException;
import com.hoprxi.application.SearchException;
import com.hoprxi.domain.model.Area;
import com.hoprxi.infrastructure.JsonByteBufOutputStream;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.publisher.Sinks;
import salt.hoprxi.crypto.application.DecryptUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.2 builder 2026-07-11
 */
public class ESAreaQuery implements AreaQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESAreaQuery.class);
    private static final int COUNTRY_SIZE = 399;
    private static final int CHILDREN_SIZE = 199;
    private static final int BUFFER_SIZE = 2048;//2KB缓冲区
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClient CLIENT;
    private static final ExecutorService TRANSFORM_POOL = Executors.newVirtualThreadPerTaskExecutor();
    private static final int SINGLE_BUFFER_SIZE = 1024;// 2KB缓冲区
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    static {
        Config config = ConfigFactory.load("area").resolve();
        Config read = config.getConfigList("databases").getFirst();
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DecryptUtil.decrypt(entry, read.getString("user"));
        String password = DecryptUtil.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))).addHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8");
        COMMON_OPTIONS = builder.build();
        CLIENT = RestClient.builder(new HttpHost(host, port, "https")).build();
    }


    @Override
    public InputStream find(int code) {
        Request request = new Request("GET", "/area/_doc/" + code);//PREFIX+"/_doc/"
        request.setOptions(COMMON_OPTIONS);
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(BUFFER_SIZE);
        boolean success = false;
        try {
            Response response = CLIENT.performRequest(request);
            try (OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator generator = JSON_FACTORY.createGenerator(os);
                 InputStream is = response.getEntity().getContent(); JsonParser parser = JSON_FACTORY.createParser(is)) {
                ESAreaQuery.extractSourceSkipMeta(parser, generator);
                success = true;
                return new ByteBufInputStream(buffer, true);
            }
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                LOGGER.warn("Item not found in Elasticsearch: id={}", code);
                throw new AreaSearchException(String.format("The item(id=%s) not found", code));
            }
            LOGGER.error("Elasticsearch error for id={}", code, e);
            throw new RuntimeException("Elasticsearch internal error", e);
        } catch (IOException e) {
            LOGGER.error("I/O failed", e);
            throw new RuntimeException("Error: Elasticsearch timeout or no connection", e);
        } finally {
            if (!success && buffer.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(buffer);
            }
        }
    }

    @Override
    public Mono<ByteBuf> findAsync(int code) {
        Request request = new Request("GET", "/area/_doc/" + code);//PREFIX+"/_doc/"
        request.setOptions(COMMON_OPTIONS);
        return ESAreaQuery.toMonoByteBuf(request, String.valueOf(code));
    }

    private static Mono<ByteBuf> toMonoByteBuf(Request request, String tips) {
        return Mono.create((MonoSink<ByteBuf> sink) -> {
                    final AtomicBoolean isCancelled = new AtomicBoolean(false);
                    // 取消监听
                    sink.onCancel(() -> isCancelled.set(true));
                    // ES 异步请求
                    CLIENT.performRequestAsync(request, new ResponseListener() {
                        @Override
                        public void onSuccess(Response response) {
                            if (isCancelled.get()) {
                                EntityUtils.consumeQuietly(response.getEntity());
                                return;
                            }
                            final InputStream content;
                            try {
                                content = response.getEntity().getContent();
                            } catch (IOException e) {
                                sink.error(ESAreaQuery.mapException(e, tips));
                                EntityUtils.consumeQuietly(response.getEntity());
                                return;
                            }
                            // 线程池处理
                            TRANSFORM_POOL.execute(() -> {
                                if (isCancelled.get()) {
                                    try {
                                        if (content != null) content.close();
                                    } catch (Exception ignore) {
                                    }
                                    EntityUtils.consumeQuietly(response.getEntity());
                                    return;
                                }
                                ByteBuf buf = PooledByteBufAllocator.DEFAULT.directBuffer(SINGLE_BUFFER_SIZE);
                                try (content; JsonParser parser = JSON_FACTORY.createParser(content);
                                     OutputStream os = new ByteBufOutputStream(buf); JsonGenerator generator = JSON_FACTORY.createGenerator(os)) {

                                    ESAreaQuery.extractSourceSkipMeta(parser, generator);

                                    if (!isCancelled.get()) {
                                        sink.success(buf);
                                    } else {
                                        ReferenceCountUtil.release(buf);
                                    }
                                } catch (IOException e) {
                                    ReferenceCountUtil.release(buf);
                                    if (!isCancelled.get()) {
                                        sink.error(ESAreaQuery.mapException(e, tips));
                                    }
                                }
                            });
                        }

                        @Override
                        public void onFailure(Exception exception) {
                            if (!isCancelled.get()) {
                                sink.error(ESAreaQuery.mapException(exception, tips));
                            }
                        }
                    });
                })
                .doOnTerminate(() -> LOGGER.debug("Request terminated from {}", tips))
                .doOnDiscard(ByteBuf.class, ReferenceCountUtil::safeRelease);
    }

    private static Throwable mapException(Exception err, Object identifier) {
        Throwable cause = err;
        if (err instanceof UncheckedIOException) {
            cause = err.getCause(); // 解包 IO 异常
        }
        if (cause instanceof ResponseException) {
            int status = ((ResponseException) cause).getResponse().getStatusLine().getStatusCode();
            if (status == 404) {
                return new NotFoundException("Not found for: " + identifier);
            } else if (status >= 400 && status < 500) {
                return new SearchException("Client error: " + status);
            } else {
                return new SearchException("Server error: " + status);
            }
        } else if (cause instanceof IOException) {
            LOGGER.error("I/O error for id={}", identifier, cause);
            return new SearchException("Network error", cause);
        }
        return new SearchException("Unexpected error", cause);
    }

    private static void extractSourceSkipMeta(JsonParser parser, JsonGenerator generator) throws IOException {
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME && "_source".equals(parser.currentName())) {
                parser.nextToken(); // move to value (START_OBJECT)
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    throw new IllegalStateException("_source is not an object");
                }
                generator.writeStartObject();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() == JsonToken.FIELD_NAME && "_meta".equals(parser.currentName())) {
                        parser.nextToken();
                        parser.skipChildren(); // skip value of _meta
                    } else {
                        generator.copyCurrentEvent(parser); // copy field name
                        parser.nextToken();
                        generator.copyCurrentStructure(parser); // copy entire value (handles nested)
                    }
                }
                generator.writeEndObject();
            }
        }
        generator.close();
    }

    @Override
    public InputStream country() {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildCountryQueryRequest());
        return ESAreaQuery.fetchAreaDataStream(request);
    }

    @Override
    public Flux<ByteBuf> countryAsync() {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildCountryQueryRequest());
        return ESAreaQuery.toFluxByteBuf(request, "country");
    }

    private static String buildCountryQueryRequest() {
        try (StringWriter writer = new StringWriter(); JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeObjectFieldStart("runtime_mappings");
            generator.writeObjectFieldStart("is_self_parent");
            generator.writeStringField("type", "boolean");
            generator.writeStringField("script", "emit(doc['code'].value == doc['parent_code'].value)");
            generator.writeEndObject();//is_self_parent
            generator.writeEndObject();//end runtime

            generator.writeObjectFieldStart("query");
            generator.writeObjectFieldStart("term");
            generator.writeBooleanField("is_self_parent", true);
            generator.writeEndObject();//term
            generator.writeEndObject();//end query

            generator.writeFieldName("sort"); // 添加 sort 数组
            generator.writeStartArray(); // [
            generator.writeStartObject(); // {
            generator.writeStringField("code", "asc");
            generator.writeEndObject(); // }
            generator.writeEndArray(); // ]

            generator.writeEndObject();//end root
            generator.close();
            return writer.toString();
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
            throw new IllegalStateException("Cannot assemble request JSON");
        }
    }

    @Override
    public InputStream query(String keyword, EnumSet<Area.Level> filters, int from, int size) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildQueryRequest(keyword, filters, from, size));
        return ESAreaQuery.fetchAreaDataStream(request);
    }

    @Override
    public Flux<ByteBuf> queryAsync(String keyword, EnumSet<Area.Level> filters, int from, int size) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildQueryRequest(keyword, filters, from, size));
        return ESAreaQuery.toFluxByteBuf(request, keyword);
    }

    private static String buildQueryRequest(String key, EnumSet<Area.Level> filters, int from, int size) {
        try (StringWriter writer = new StringWriter(); JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject(); // 开始生成整个JSON对象
            // 分页
            generator.writeNumberField("from", from);
            generator.writeNumberField("size", size);

            generator.writeObjectFieldStart("query"); // 写入query对象
            generator.writeObjectFieldStart("bool"); // 写入bool对象

            if (filters != null && !filters.isEmpty()) {// 写入must数组
                generator.writeArrayFieldStart("must");
                generator.writeStartObject();
                generator.writeObjectFieldStart("terms");
                generator.writeArrayFieldStart("level.name");
                for (Area.Level level : filters) {
                    generator.writeString(level.name());
                }
                generator.writeEndArray(); // 结束 level.name 数组
                generator.writeEndObject(); // 结束 terms
                generator.writeEndObject();
                generator.writeEndArray(); // 结束 must 数组
            }

            generator.writeArrayFieldStart("should");  // 写入should数组
            generator.writeStartObject();
            generator.writeObjectFieldStart("multi_match");// 写入multi_match对象
            generator.writeStringField("query", key);
            generator.writeArrayFieldStart("fields");// 写入fields数组
            generator.writeString("name.name^3");
            generator.writeString("name.abbreviation^2");
            generator.writeString("name.alias");
            generator.writeEndArray();//end fields
            generator.writeStringField("type", "best_fields");
            generator.writeNumberField("boost", 2.0);
            generator.writeEndObject(); // 结束multi_match对象
            generator.writeEndObject(); // 结束should数组中的第一个对象

            generator.writeStartObject();
            generator.writeObjectFieldStart("multi_match");// 写入multi_match对象
            generator.writeStringField("query", key);
            generator.writeArrayFieldStart("fields");// 写入fields数组
            generator.writeString("name.name.pinyin^3");
            generator.writeString("name.abbreviation.pinyin^2");
            generator.writeString("name.alias.pinyin");
            generator.writeEndArray();//end fields
            generator.writeEndObject(); // 结束multi_match对象
            generator.writeEndObject(); // 结束should数组中的第一个对象

            generator.writeStartObject();//开始should数组中的第三个对象
            generator.writeObjectFieldStart("prefix");// 写入prefix对象
            generator.writeObjectFieldStart("code.search");
            generator.writeStringField("value", key);
            generator.writeNumberField("boost", 2.0);
            generator.writeEndObject(); // 结束code.search对象
            generator.writeEndObject(); // 结束prefix对象
            generator.writeEndObject();

            // 结束should数组中的第二个对象
            generator.writeEndArray(); // 结束should数组
            generator.writeNumberField("minimum_should_match", 1);// 写入minimum_should_match字段
            generator.writeEndObject(); // 结束bool对象
            generator.writeEndObject(); // 结束query对象
            // sort 结构
            generator.writeArrayFieldStart("sort");
            generator.writeStartObject(); // 排序：code
            generator.writeStringField("code", "asc");
            generator.writeEndObject();
            generator.writeEndArray();//end sort

            generator.writeEndObject(); // 结束整个JSON对象
            generator.close();
            return writer.toString();
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
            throw new RuntimeException("Cannot assemble request JSON", e);
        }
    }

    public InputStream query(EnumSet<Area.Level> filters, String searchAfter, int size) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildQueryRequest(filters, searchAfter, size));
        return ESAreaQuery.fetchAreaDataStream(request);
    }

    @Override
    public Flux<ByteBuf> queryAsync(EnumSet<Area.Level> filters, String searchAfter, int size) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildQueryRequest(filters, searchAfter, size));
        return ESAreaQuery.toFluxByteBuf(request, "");
    }

    private static String buildQueryRequest(EnumSet<Area.Level> filters, String searchAfter, int size) {
        try (StringWriter writer = new StringWriter(); JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeNumberField("size", size);   // size 字段
            generator.writeObjectFieldStart("query");// query 对象
            if (filters != null && !filters.isEmpty()) {//有过滤要求
                generator.writeObjectFieldStart("bool");
                generator.writeArrayFieldStart("must");             // must 数组
                generator.writeStartObject();//有must需要包装一个bool查询到对象
            }
            //  条件：match_all 查询
            generator.writeObjectFieldStart("match_all");
            generator.writeEndObject();// 结束 match_all
            if (filters != null && !filters.isEmpty()) {
                generator.writeEndObject();//结束第一个must的bool外包
                generator.writeStartObject();
                generator.writeObjectFieldStart("terms");
                generator.writeArrayFieldStart("level.name");
                for (Area.Level level : filters) {
                    generator.writeString(level.name());
                }
                generator.writeEndArray(); // 结束 level.name 数组
                generator.writeEndObject(); // 结束 terms
                generator.writeEndObject(); // 结束 terms 对象
                generator.writeEndArray(); // 结束 must 数组
                generator.writeEndObject(); // 结束 bool
            }
            generator.writeEndObject(); // 结束 query
            // sort 数组
            generator.writeArrayFieldStart("sort");
            generator.writeStartObject();
            generator.writeStringField("code", "asc");
            generator.writeEndObject();
            generator.writeEndArray(); // 结束 sort 数组

            if (searchAfter != null && !searchAfter.isBlank()) {// search_after 数组
                generator.writeArrayFieldStart("search_after");
                generator.writeNumber(searchAfter);
                generator.writeEndArray(); // 结束 search_after 数组
            }
            generator.writeEndObject();
            generator.close();
            return writer.toString();
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
            throw new RuntimeException("Cannot assemble request JSON", e);
        }
    }

    @Override
    public InputStream children(int code) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildChildrenQueryRequest(code));
        return ESAreaQuery.fetchAreaDataStream(request);
    }

    @Override
    public Flux<ByteBuf> childrenAsync(int code) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildChildrenQueryRequest(code));
        return ESAreaQuery.toFluxByteBuf(request,String.valueOf(code));
    }

    private static String buildChildrenQueryRequest(int code) {
        try (StringWriter writer = new StringWriter(); JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();// 开始生成 JSON
            generator.writeNumberField("size", CHILDREN_SIZE);
            // query 部分
            generator.writeObjectFieldStart("query");

            generator.writeObjectFieldStart("bool"); // "bool": {
            // 开始 "should" 数组 (对应 OR 关系)
            generator.writeArrayFieldStart("should");
            // --- 条件 1: code = 1001 (自己) ---
            generator.writeStartObject(); // {
            generator.writeObjectFieldStart("term"); // "term": {
            generator.writeNumberField("code", code); // "code": 1001
            generator.writeEndObject(); // } 结束 term
            generator.writeEndObject(); // } 结束第一个条件对象
            // --- 条件 2: parent_code = 1001 (子辖区) ---
            generator.writeStartObject(); // {
            generator.writeObjectFieldStart("term"); // "term": {
            generator.writeNumberField("parent_code", code); // "parent_code": 1001
            generator.writeEndObject(); // } 结束 term
            generator.writeEndObject(); // } 结束第二个条件对象
            generator.writeEndArray(); // ] 结束 should 数组
            // (可选) 显式指定至少匹配一个，虽然默认就是 1
            // generator.writeNumberField("minimum_should_match", 1);
            generator.writeEndObject(); // } 结束 bool

            generator.writeEndObject(); // 结束 query
            // sort 部分
            generator.writeArrayFieldStart("sort");
            generator.writeStartObject();
            generator.writeStringField("code", "asc");
            generator.writeEndObject();
            generator.writeEndArray();

            generator.writeEndObject(); // 结束根对象
            generator.close();
            return writer.toString();
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
            throw new RuntimeException("Cannot assemble request JSON", e);
        }
    }

    private static InputStream fetchAreaDataStream(Request request) {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(BUFFER_SIZE);
        boolean success = false;
        try {
            Response response = CLIENT.performRequest(request);
            try (InputStream is = response.getEntity().getContent(); JsonParser parser = JSON_FACTORY.createParser(is);
                 OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os)) {
                ESAreaQuery.transformAreaSearchResponse(parser, gen);
                success = true;
                return new ByteBufInputStream(buffer, true);
            }
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                LOGGER.warn("Area not found in Elasticsearch");
                throw new AreaSearchException("No country not found");
            } else {
                LOGGER.error("Elasticsearch internal error", e);
                throw new RuntimeException("Elasticsearch internal error", e);
            }
        } catch (IOException e) {
            LOGGER.error("I/O failed", e);
            throw new RuntimeException("Error: Elasticsearch timeout or no connection", e);
        } finally {
            if (!success && buffer.refCnt() > 0) {
                ReferenceCountUtil.safeRelease(buffer);
            }
        }
    }

    private static Flux<ByteBuf> toFluxByteBuf(Request request, String tips) {
        AtomicBoolean isCancelled = new AtomicBoolean(false);
        Sinks.Many<ByteBuf> sink = Sinks.many().unicast().onBackpressureBuffer();  // 使用单播接收器（更高效）
        CLIENT.performRequestAsync(request, new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                if (isCancelled.get()) {
                    EntityUtils.consumeQuietly(response.getEntity());
                    return;
                }
                final InputStream content;
                try {
                    content = response.getEntity().getContent();
                } catch (IOException e) {
                    sink.tryEmitError(ESAreaQuery.mapException(e, tips));
                    EntityUtils.consumeQuietly(response.getEntity());
                    return;
                }
                TRANSFORM_POOL.execute(() -> {
                    if (isCancelled.get()) {
                        try {
                            if (content != null) content.close();
                        } catch (Exception ignore) {
                        }
                        EntityUtils.consumeQuietly(response.getEntity());
                        return;
                    }
                    try (content; JsonParser parser = JSON_FACTORY.createParser(content);
                         OutputStream os = new JsonByteBufOutputStream(sink, isCancelled); JsonGenerator generator = JSON_FACTORY.createGenerator(os)) {

                        ESAreaQuery.transformAreaSearchResponse(parser, generator);
                        Sinks.EmitResult result = sink.tryEmitComplete();

                        if (result.isFailure() && result != Sinks.EmitResult.FAIL_TERMINATED) {
                            LOGGER.warn("Failed to emit complete: {}", result);
                        }
                    } catch (IOException e) {
                        Sinks.EmitResult result = sink.tryEmitError(e);
                        if (result.isFailure() && result != Sinks.EmitResult.FAIL_TERMINATED) {
                            LOGGER.warn("emitError failed: {}", result);
                        }
                    } finally {
                        EntityUtils.consumeQuietly(response.getEntity());
                    }
                });
            }

            @Override
            public void onFailure(Exception exception) {
                if (!isCancelled.get()) {
                    sink.tryEmitError(ESAreaQuery.mapException(exception, tips));
                }
            }
        });

        return sink.asFlux()
                .timeout(Duration.ofSeconds(20), Mono.error(new TimeoutException("Request timed out for : " + tips)))
                .doOnCancel(() -> isCancelled.set(true)).doOnTerminate(() -> LOGGER.debug("Request terminated for {}", tips))
                .doOnDiscard(ByteBuf.class, ReferenceCountUtil::safeRelease);
    }

    private static void transformAreaSearchResponse(JsonParser parser, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        Integer total = null;
        boolean inHitsArray = false;
        // State: 0 = root, 1 = in top-level "hits" object, 2 = in "hits.hits" array
        int state = 0;

        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();

                if (state == 0 && "hits".equals(name)) {
                    parser.nextToken(); // consume value (should be START_OBJECT)
                    if (parser.currentToken() == JsonToken.START_OBJECT) {
                        state = 1; // entered top-level hits object
                    } else {
                        parser.skipChildren();
                    }
                } else if (state == 1) {
                    parser.nextToken(); // consume field value
                    if ("total".equals(name)) {
                        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                            total = parser.getValueAsInt();
                        } else if (parser.currentToken() == JsonToken.START_OBJECT) {
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                if (parser.currentToken() == JsonToken.FIELD_NAME && "value".equals(parser.currentName())) {
                                    parser.nextToken();
                                    total = parser.getValueAsInt();
                                } else {
                                    parser.skipChildren();
                                }
                            }
                        }
                    } else if ("hits".equals(name)) {
                        if (parser.currentToken() == JsonToken.START_ARRAY) {
                            // Now we are at the start of hits.hits array
                            gen.writeNumberField("total", total != null ? total : 0);
                            gen.writeArrayFieldStart("areas");
                            inHitsArray = true;
                            break; // exit to process array manually
                        } else {
                            parser.skipChildren();
                        }
                    } else {
                        parser.skipChildren();
                    }
                } else {
                    parser.skipChildren();
                }
            }
        }
        // If we broke out because we found hits array
        if (inHitsArray) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    throw new IllegalStateException("Expected hit object");
                }

                gen.writeStartObject();

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() == JsonToken.FIELD_NAME) {
                        String fieldName = parser.currentName();
                        parser.nextToken();

                        if ("_source".equals(fieldName)) {
                            if (parser.currentToken() != JsonToken.START_OBJECT) {
                                throw new IllegalStateException("_source must be object");
                            }
                            while (parser.nextToken() != JsonToken.END_OBJECT) { // Flatten _source
                                gen.copyCurrentEvent(parser); // copy field name
                                parser.nextToken();
                                gen.copyCurrentStructure(parser); // copy entire value (handles nested)
                            }
                        } else if ("sort".equals(fieldName)) {
                            gen.writeFieldName("sort");
                            gen.copyCurrentStructure(parser);
                        } else {
                            parser.skipChildren();
                        }
                    }
                }
                gen.writeEndObject();
            }
            gen.writeEndArray();
        } else {
            // No hits array found
            gen.writeNumberField("total", total != null ? total : 0);
            gen.writeArrayFieldStart("areas");
            gen.writeEndArray();
        }

        gen.writeEndObject();
        gen.close();
    }
}