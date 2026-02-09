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
package com.hoprxi.infrastructure.persistence;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.elasticsearch.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.application.DatabaseSpecDecrypt;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/18
 */

public class ESAreaRepository implements AreaRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESAreaRepository.class);
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

    @Override
    public Area find(int code) {
        try {
            Request request = new Request("GET", "/area/_doc/" + code);
            request.setOptions(COMMON_OPTIONS);
            Response response = CLIENT.performRequest(request);
            try (InputStream is = response.getEntity().getContent();
                 JsonParser parser = JSON_FACTORY.createParser(is)) {
                while (parser.nextToken() != null) {
                    if (parser.currentToken() == JsonToken.FIELD_NAME && "_source".equals(parser.currentName())) {
                        parser.nextToken(); // move to value (START_OBJECT)
                        if (parser.currentToken() != JsonToken.START_OBJECT) {
                            throw new IllegalStateException("_source is not an object");
                        }
                        return rebuild(parser);
                    }
                }
            }
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return null; // 确实不存在
            } else {
                LOGGER.error("ES server error for area(code={})", code, e);
                throw new RuntimeException("Failed to retrieve area", e); // 或其他处理
            }
        } catch (IOException e) {
            LOGGER.error("Network or IO error for area(code={})", code, e);
            throw new RuntimeException("IO error", e);
        }
        return null;
    }

    private Area rebuild(JsonParser parser) throws IOException {
        int code = 0, parentCode = 0;
        Name name = null;
        String zipcode = null, telephoneCode = null;
        WGS84 wgs84 = null;
        Area.Level level = Area.Level.TOWN;
        while (parser.nextToken() != null) {
            if (parser.currentToken() == JsonToken.END_OBJECT && "_source".equals(parser.currentName()))//防止超_source范围
                break;
            if (JsonToken.FIELD_NAME.equals(parser.currentToken())) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "code" -> code = parser.getIntValue();
                    case "parent_code" -> parentCode = parser.getIntValue();
                    case "name" -> name = nameOf(parser);
                    case "zipcode" -> zipcode = parser.getValueAsString();
                    case "telephoneCode" -> telephoneCode = parser.getValueAsString();
                    case "location" -> wgs84 = wgs84Of(parser);
                    case "level" -> level = levelOf(parser);
                }
            }
        }
        return switch (level.name()) {
            case "COUNTRY" -> new Country(code, parentCode, name, wgs84, zipcode, telephoneCode);
            case "PROVINCE" -> new Province(code, parentCode, name, wgs84, zipcode, telephoneCode);
            case "CITY" -> new City(code, parentCode, name, wgs84, zipcode, telephoneCode);
            case "COUNTY" -> new County(code, parentCode, name, wgs84, zipcode, telephoneCode);
            case "TOWN" -> new Town(code, parentCode, name, wgs84, zipcode, telephoneCode);
            default -> null;
        };
    }

    private Name nameOf(JsonParser parser) throws IOException {
        String name = "", abbreviation = "", alias = "", mnemonic = "";
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "name":
                    name = parser.getValueAsString();
                    break;
                case "mnemonic":
                    mnemonic = parser.getValueAsString();
                    break;
                case "abbreviation":
                    abbreviation = parser.getValueAsString();
                    break;
                case "alias":
                    alias = parser.getValueAsString();
                    break;
            }
        }
        return new Name(name, abbreviation, mnemonic, alias);
    }

    private WGS84 wgs84Of(JsonParser parser) throws IOException {
        double longitude = 0.0, latitude = 0.0;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "lon":
                    longitude = parser.getValueAsDouble(0.0);
                    break;
                case "lat":
                    latitude = parser.getValueAsDouble(0.0);
                    break;
            }
        }
        return new WGS84(longitude, latitude);
    }

    private Area.Level levelOf(JsonParser parser) throws IOException {
        String name = "";
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            if ("name".equals(fieldName))
                name = parser.getValueAsString();
        }
        return Area.Level.valueOf(name);
    }

    @Override
    public void save(Area area) {
        try {
            Request request = new Request("POST", "/area/_update/" + area.code());
            request.setOptions(COMMON_OPTIONS);
            request.setJsonEntity(jsonEntity(area));
            CLIENT.performRequest(request);
        } catch (IOException e) {
            //System.out.println(((ResponseException)e).getResponse().getStatusLine().getStatusCode());
            LOGGER.error("The area({}) can't save", area, e);
        }
    }

    private String jsonEntity(Area area) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(writer)) {
            gen.writeStartObject();
            gen.writeObjectFieldStart("doc");
            gen.writeNumberField("code", area.code());
            gen.writeNumberField("parent_code", area.parentCode());
            gen.writeObjectFieldStart("name");
            gen.writeStringField("name", area.name().name());
            gen.writeStringField("mnemonic", area.name().mnemonic());
            gen.writeStringField("abbreviation", area.name().abbreviation());
            gen.writeStringField("alias", area.name().alias());
            gen.writeEndObject();//end name
            gen.writeStringField("zipcode", area.zipcode());
            gen.writeStringField("telephone_code", area.telephoneCode());
            gen.writeObjectFieldStart("location");
            gen.writeNumberField("lat", area.location().latitude());
            gen.writeNumberField("lon", area.location().longitude());
            gen.writeEndObject();//location
            gen.writeObjectFieldStart("level");
            gen.writeStringField("name", area.level().name());
            gen.writeNumberField("order", area.level().ordinal());
            gen.writeEndObject();//level
            gen.writeEndObject();//end doc
            gen.writeBooleanField("doc_as_upsert", true);
            gen.writeEndObject();
            gen.flush();
        } catch (IOException e) {
            LOGGER.error("The area can't be serialized for upsert", e);
        }
        return writer.toString();
    }

    @Override
    public void delete(int code) {
        try {
            Request request = new Request("DELETE", "/area/_doc/" + code);
            request.setOptions(COMMON_OPTIONS);
            Response response = CLIENT.performRequest(request);
            if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
                LOGGER.info("The area(code={}) is deleted", code);
            }
        } catch (IOException e) {
            //System.out.println(((ResponseException)e).getResponse().getStatusLine().getStatusCode());
            LOGGER.error("The area(code={}) can't delete", code, e);
        }
    }
}
