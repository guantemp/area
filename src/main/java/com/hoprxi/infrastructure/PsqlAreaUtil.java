/*
 * Copyright (c) 2023. www.hoprxi.com All Rights Reserved.
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

package com.hoprxi.infrastructure;

import com.fasterxml.jackson.core.*;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.Boundary;
import com.hoprxi.domain.model.coordinate.WGS84;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-09
 */
public final class PsqlAreaUtil  {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsqlAreaUtil.class);
    private static final JsonFactory jasonFactory = JsonFactory.builder().build();


    public static String toJson(Name name) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = jasonFactory.createGenerator(output, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeStringField("name", name.name());
            generator.writeNumberField("initials", name.initials());
            generator.writeStringField("abbreviation", name.abbreviation());
            generator.writeStringField("mnemonic", name.mnemonic());
            if (name.alias() != null && !name.alias().isEmpty())
                generator.writeStringField("alias", name.alias());
            generator.writeEndObject();
            generator.flush();
        } catch (IOException e) {
            LOGGER.error("Not write name as json", e);
        }
        return output.toString();
    }

    public static String toJson(Boundary boundary) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = jasonFactory.createGenerator(output, JsonEncoding.UTF8)) {
            //generator.writeStartArray();
            generator.writeStartObject();
            generator.writeNumberField("longitude", boundary.getCentre().longitude());
            generator.writeNumberField("latitude", boundary.getCentre().latitude());
            generator.writeEndObject();
            /*
            if (wgs84.getMin() != null) {
                generator.writeStartObject();
                generator.writeNumberField("longitude", wgs84.getMin().longitude());
                generator.writeNumberField("latitude", wgs84.getMin().latitude());
                generator.writeEndObject();
            }
            if (wgs84.getMax() != null) {
                generator.writeStartObject();
                generator.writeNumberField("longitude", wgs84.getMax().longitude());
                generator.writeNumberField("latitude", wgs84.getMax().latitude());
                generator.writeEndObject();
            }
            generator.writeEndArray();
             */
            generator.flush();
        } catch (IOException e) {
            LOGGER.error("Not write boundary as json", e);
        }
        return output.toString();
    }

    public static String toJson(WGS84 wgs84) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = jasonFactory.createGenerator(output, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField("longitude", wgs84.longitude());
            generator.writeNumberField("latitude", wgs84.latitude());
            generator.writeEndObject();
            generator.flush();
        } catch (IOException e) {
            LOGGER.error("Not write WGS84 as json", e);
        }
        return output.toString();
    }

    public static WGS84 toWgs84(String json) throws IOException {
        if (json == null)
            return null;
        double longitude = 0.0, latitude = 0.0;
        JsonParser parser = jasonFactory.createParser(json.getBytes(StandardCharsets.UTF_8));
        while (!parser.isClosed()) {
            JsonToken jsonToken = parser.nextToken();
            if (JsonToken.FIELD_NAME == jsonToken) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                switch (fieldName) {
                    case "longitude":
                        longitude = parser.getDoubleValue();
                        break;
                    case "latitude":
                        latitude = parser.getDoubleValue();
                        break;
                }
            }
        }
        return new WGS84(longitude, latitude);
    }
}
