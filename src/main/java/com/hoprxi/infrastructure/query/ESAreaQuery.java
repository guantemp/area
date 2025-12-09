/*
 * Copyright (c) 2025. www.hoprxi.com All Rights Reserved.
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

import com.fasterxml.jackson.core.*;
import com.hoprxi.application.AreaQuery;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.application.DatabaseSpecDecrypt;

import java.io.*;
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
    private static final int COUNTRY_SIZE = 333;
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClient CLIENT;
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    static {
        Config config = ConfigFactory.load("area");
        Config read = config.getConfigList("read").getFirst();
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

    public enum Level {
        COUNTRY, PROVINCE, CITY, COUNTY, TOWN;

        /**
         * @param s
         * @return <code>NULL if no match</code>
         */
        public static Level of(String s) {
            for (Level Level : values()) {
                if (Level.name().equalsIgnoreCase(s))
                    return Level;
            }
            return null;
        }
    }

    @Override
    public OutputStream query(int code) {
        try (OutputStream os = new ByteArrayOutputStream(128); JsonGenerator generator = JSON_FACTORY.createGenerator(os, JsonEncoding.UTF8)) {
            Request request = new Request("GET", "/area/_doc/" + code);
            request.setOptions(COMMON_OPTIONS);
            Response response = CLIENT.performRequest(request);
            JsonParser parser = JSON_FACTORY.createParser(response.getEntity().getContent());
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT && "_source".equals(parser.getCurrentName())) {
                    generator.writeStartObject();
                    while (parser.nextToken() != null) {
                        if (parser.currentToken() == JsonToken.END_OBJECT && "_source".equals(parser.getCurrentName()))
                            break;
                        generator.copyCurrentEvent(parser);
                    }
                    generator.writeEndObject();
                    break;
                }
            }
            return os;
        } catch (IOException e) {
            //System.out.println(((ResponseException)e).getResponse().getStatusLine().getStatusCode());
            LOGGER.error("The area(code={}) can't retrieve", code, e);
        }
        return new ByteArrayOutputStream(0);
    }

    public OutputStream queryCountry() {
        try {
            Request request = new Request("GET", "/area/_search");
            request.setOptions(COMMON_OPTIONS);
            request.setJsonEntity(countryJsonEntity());
            Response response = CLIENT.performRequest(request);
            return writeAreas(response.getEntity().getContent());
        } catch (IOException e) {
            LOGGER.error("The area(country) can't retrieve", e);
        }
        return new ByteArrayOutputStream(0);
    }

    private String countryJsonEntity() {
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

    public OutputStream query(String key, EnumSet<Level> filters, int from, int size) {
        try {
            Request request = new Request("GET", "/area/_search");
            request.setOptions(COMMON_OPTIONS);
            //System.out.println(nameJsonEntity(name, filters, from, size));
            request.setJsonEntity(keyJsonEntity(key, filters, from, size));
            Response response = CLIENT.performRequest(request);
            return writeAreas(response.getEntity().getContent());
        } catch (IOException e) {
            LOGGER.error("The area(country) can't retrieve", e);
        }
        return new ByteArrayOutputStream(0);
    }

    private String keyJsonEntity(String key, EnumSet<Level> filters, int from, int size) {
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
            generator.writeFieldName("sort");
            generator.writeStartArray();
            // 第一级排序：level.order
            generator.writeStartObject();
            generator.writeStringField("level.order", "asc");
            generator.writeEndObject();
            // 第二级排序：parent_code
            generator.writeStartObject();
            generator.writeStringField("parent_code", "asc");
            generator.writeEndObject();
            generator.writeEndArray();

            generator.writeEndObject(); // 结束整个JSON对象
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
        }
        return writer.toString();
    }

    public OutputStream query(EnumSet<Level> filters, String searchAfter, int size) {
        try {
            Request request = new Request("GET", "/area/_search");
            request.setOptions(COMMON_OPTIONS);
            request.setJsonEntity(jsonEntity(filters, searchAfter, size));
            Response response = CLIENT.performRequest(request);
            return writeAreas(response.getEntity().getContent());
        } catch (IOException e) {
            LOGGER.error("The area(country) can't retrieve", e);
        }
        return new ByteArrayOutputStream(0);
    }

    private String jsonEntity(EnumSet<Level> filters, String searchAfter, int size) {
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

    public OutputStream queryJurisdiction(int code) {
        try {
            Request request = new Request("GET", "/area/_search");
            request.setOptions(COMMON_OPTIONS);
            request.setJsonEntity(jurisdictionJsonEntity(code));
            Response response = CLIENT.performRequest(request);
            return writeAreas(response.getEntity().getContent());
        } catch (IOException e) {
            LOGGER.error("The area(jurisdiction) can't retrieve", e);
        }
        return new ByteArrayOutputStream(0);
    }

    private String jurisdictionJsonEntity(int code) {
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


    private OutputStream writeAreas(InputStream is) throws IOException {
        OutputStream os = new ByteArrayOutputStream();
        try (JsonParser parser = JSON_FACTORY.createParser(is); JsonGenerator generator = JSON_FACTORY.createGenerator(os)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME && "hits".equals(parser.getCurrentName())) {
                    generator.writeStartObject();
                    while (parser.nextToken() != null) {
                        if (parser.currentToken() == JsonToken.FIELD_NAME && "total".equals(parser.getCurrentName())) {
                            while (parser.nextToken() != null) {
                                if (parser.currentToken() == JsonToken.FIELD_NAME && "value".equals(parser.getCurrentName())) {
                                    parser.nextToken();
                                    generator.writeNumberField("total", parser.getValueAsInt());
                                    break;
                                }
                            }
                        }
                        if (parser.currentToken() == JsonToken.START_ARRAY && "hits".equals(parser.getCurrentName())) {
                            generator.writeArrayFieldStart("areas");
                            while (parser.nextToken() != null) {
                                if (parser.getCurrentToken() == JsonToken.START_OBJECT) {
                                    generator.writeStartObject();
                                    writeSource(parser, generator);
                                    writeSort(parser, generator);
                                    generator.writeEndObject();
                                }
                                if (parser.currentToken() == JsonToken.END_ARRAY && "hits".equals(parser.getCurrentName())) {
                                    break;
                                }
                            }
                            generator.writeEndArray();
                        }
                    }
                    generator.writeEndObject();
                }
            }
        }
        return os;
    }

    private void writeSource(JsonParser parser, JsonGenerator generator) throws IOException {
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.START_OBJECT && "_source".equals(parser.getCurrentName())) {
                while (parser.nextToken() != null) {
                    if (parser.currentToken() == JsonToken.END_OBJECT && "_source".equals(parser.getCurrentName()))
                        break;
                    generator.copyCurrentEvent(parser);
                }
            }
            if (parser.currentToken() == JsonToken.END_OBJECT && "_source".equals(parser.getCurrentName()))
                break;
        }
    }

    private void writeSort(JsonParser parser, JsonGenerator generator) throws IOException {
        if (parser.nextToken() == JsonToken.FIELD_NAME && "sort".equals(parser.getCurrentName())) {
            generator.copyCurrentEvent(parser);
            while (parser.nextToken() != null) {
                generator.copyCurrentEvent(parser);
                if (parser.currentToken() == JsonToken.END_ARRAY && "sort".equals(parser.getCurrentName()))
                    break;
            }
        }
    }
}