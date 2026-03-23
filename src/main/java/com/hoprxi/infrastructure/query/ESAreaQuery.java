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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.elasticsearch.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.application.DatabaseSpecDecrypt;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumSet;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-11
 */
public class ESAreaQuery implements AreaQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESAreaQuery.class);
    private static final int COUNTRY_SIZE = 299;
    private static final int BUFFER_SIZE = 2048;//2KB缓冲区
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClient CLIENT;
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    static {
        Config config = ConfigFactory.load("area").resolve();
        Config read = config.getConfigList("databases").getFirst();
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DatabaseSpecDecrypt.decrypt(entry, read.getString("user"));
        String password = DatabaseSpecDecrypt.decrypt(entry, read.getString("password"));

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
            ;
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
        generator.flush();
    }

    @Override
    public InputStream queryCountry() {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildCountryQueryRequest());
        return ESAreaQuery.extract(request);
    }

    private static String buildCountryQueryRequest() {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();  // 开始生成整个JSON对象
            generator.writeNumberField("size", COUNTRY_SIZE);// 添加 size 字段
            generator.writeFieldName("query"); // 添加 query 结构
            generator.writeStartObject(); // {
            generator.writeFieldName("bool");
            generator.writeStartObject(); // {
            generator.writeFieldName("filter");
            generator.writeStartObject(); // {
            generator.writeFieldName("script");
            generator.writeStartObject(); // {
            generator.writeFieldName("script");
            generator.writeStartObject(); // {
            generator.writeStringField("lang", "painless");
            generator.writeStringField("source", "doc['code'].value == doc['parent_code'].value");
            generator.writeEndObject(); // }
            generator.writeEndObject(); // }
            generator.writeEndObject(); // }
            generator.writeEndObject(); // }
            generator.writeEndObject(); // }

            generator.writeFieldName("sort"); // 添加 sort 数组
            generator.writeStartArray(); // [
            generator.writeStartObject(); // {
            generator.writeStringField("code", "asc");
            generator.writeEndObject(); // }
            generator.writeEndArray(); // ]
            generator.writeEndObject(); // 结束整个JSON对象
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
        }
        return writer.toString();
    }

    public InputStream query(String key, EnumSet<Level> filters, int from, int size) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildkeyQueryRequest(key, filters, from, size));
        return ESAreaQuery.extract(request);
    }

    private static String buildkeyQueryRequest(String key, EnumSet<Level> filters, int from, int size) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject(); // 开始生成整个JSON对象
            // 分页参数
            generator.writeNumberField("from", from);
            generator.writeNumberField("size", size);
            // query 结构
            generator.writeObjectFieldStart("query");
            if (filters != null && !filters.isEmpty()) {//有过滤要求
                generator.writeObjectFieldStart("bool");
                generator.writeArrayFieldStart("must");             // must 数组
                generator.writeStartObject();//有must需要包装一个bool查询到对象
            }
            // 必须有的 bool 查询
            generator.writeObjectFieldStart("bool");
            generator.writeFieldName("should");
            generator.writeStartArray();
            // 第一个 should 条件：multi_match
            generator.writeStartObject();
            generator.writeObjectFieldStart("multi_match");
            generator.writeStringField("query", key);
            generator.writeFieldName("fields");
            generator.writeStartArray();
            generator.writeString("name.name");
            generator.writeString("name.abbreviation");
            generator.writeEndArray();
            generator.writeEndObject();
            generator.writeEndObject();
            // 第二个 should 条件：term
            generator.writeStartObject();
            generator.writeObjectFieldStart("term");
            generator.writeStringField("name.mnemonic", key);
            generator.writeEndObject();
            generator.writeEndObject();

            generator.writeEndArray(); // 结束 should 数组
            generator.writeEndObject(); // 结束 bool

            if (filters != null && !filters.isEmpty()) {
                generator.writeEndObject();//结束第一个must的bool外包
                generator.writeStartObject();
                generator.writeObjectFieldStart("terms");
                generator.writeArrayFieldStart("level.name");
                for (Level level : filters) {
                    generator.writeString(level.name());
                }
                generator.writeEndArray(); // 结束 level.name 数组
                generator.writeEndObject(); // 结束 terms
                generator.writeEndObject(); // 结束 terms 对象
                generator.writeEndArray(); // 结束 must 数组
                generator.writeEndObject(); // 结束 bool
            }
            generator.writeEndObject(); // 结束 query

            // sort 结构
            generator.writeArrayFieldStart("sort");
            // 第一级排序：level.order
            generator.writeStartObject();
            generator.writeStringField("level.order", "asc");
            generator.writeEndObject();
            // 第二级排序：parent_code
            generator.writeStartObject();
            generator.writeStringField("code", "asc");
            generator.writeEndObject();
            generator.writeEndArray();

            generator.writeEndObject(); // 结束整个JSON对象
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
        }
        return writer.toString();
    }

    public InputStream query(EnumSet<Level> filters, String searchAfter, int size) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildQueryRequest(filters, searchAfter, size));
        return ESAreaQuery.extract(request);
    }

    private static String buildQueryRequest(EnumSet<Level> filters, String searchAfter, int size) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
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
                for (Level level : filters) {
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
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
        }
        return writer.toString();
    }

    public InputStream queryJurisdiction(int code) {
        Request request = new Request("GET", "/area/_search");
        request.setOptions(COMMON_OPTIONS);
        request.setJsonEntity(ESAreaQuery.buildJurisdictionQueryRequest(code));
        return ESAreaQuery.extract(request);
    }

    private static String buildJurisdictionQueryRequest(int code) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();// 开始生成 JSON
            generator.writeNumberField("size", 199);
            // query 部分
            generator.writeObjectFieldStart("query");
            generator.writeObjectFieldStart("term");
            generator.writeNumberField("parent_code", code);
            generator.writeEndObject(); // 结束 term
            generator.writeEndObject(); // 结束 query
            // sort 部分
            generator.writeArrayFieldStart("sort");
            generator.writeStartObject();
            generator.writeStringField("code", "asc");
            generator.writeEndObject();
            generator.writeEndArray();

            generator.writeEndObject(); // 结束根对象
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
        }
        return writer.toString();
    }

    private static InputStream extract(Request request) {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(BUFFER_SIZE);
        boolean success = false;
        try {
            Response response = CLIENT.performRequest(request);
            try (InputStream is = response.getEntity().getContent(); JsonParser parser = JSON_FACTORY.createParser(is);
                 OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os)) {
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
                                    // Flatten _source
                                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                                        if (parser.currentToken() == JsonToken.FIELD_NAME) {
                                            String key = parser.currentName();
                                            gen.writeFieldName(key);
                                            parser.nextToken();
                                            gen.copyCurrentStructure(parser);
                                        }
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
                gen.flush();
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
}