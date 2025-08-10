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

package com.hoprxi.rest;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.hoprxi.application.AreaQuery;
import com.hoprxi.application.AreaView;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.Boundary;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.persistence.PsqlAreaRepository;
import com.hoprxi.infrastructure.query.ESAreaQuery;
import com.hoprxi.infrastructure.query.PsqlAreaQuery;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import salt.hoprxi.utils.NumberHelper;

import java.io.*;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2023-02-11
 *          <p>
 *          restful http<br/>
 *          areas return all country <br/>
 *          areas/code return area where key=area.code,such as:areas/51000
 *          areas/code/juri(jurisdiction) return jurisdiction area where area code,such as:areas/51000/juri
 *          <br/>
 *          <ul>
 *          parameter:
 *          <li>query=name、abbreviation、mnemonic and filters=country,province,city,county,town</li>
 *          <li>fields=name,pinyin,abbreviation, initials,alias, wgs84, zipcode,telephoneCode</li>
 *          </ul>
 *          </p>
 */
@WebServlet(urlPatterns = {"/v1/areas/*"}, name = "areas")
public class AreasServlet extends HttpServlet {
    //private static final String[] FIELDS = {"name", "zipcode", "telephoneCode"};

    //static {
    //     Arrays.sort(FIELDS, String.CASE_INSENSITIVE_ORDER);
    // }
    private static final int OFFSET = 0;
    private static final int SIZE = 64;
    private final JsonFactory JSON_FACTORY = JsonFactory.builder().build();
    private final AreaQuery query = new PsqlAreaQuery();
    private final ESAreaQuery query1 = new ESAreaQuery();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        /*
        String[] fields = this.fileds(request);
        String sort = this.sortBy(request);
        boolean asc = true;
        if (null != request.getParameter("sort")) {
            String[] s = request.getParameter("sort").split(",");
            sort = s[0];
            if (s.length == 2 && "desc".equalsIgnoreCase(s[1])) {
                asc = false;
            }
        }
         */
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(response.getOutputStream(), JsonEncoding.UTF8)) {
            boolean pretty = NumberHelper.booleanOf(request.getParameter("pretty"));
            if (pretty) generator.useDefaultPrettyPrinter();
            String pathInfo = request.getPathInfo();
            if (pathInfo == null) {
                String query = request.getParameter("q");
                AreaView[] views;
                if (query != null) {
                    int offset = NumberHelper.intOf(request.getParameter("offset"), OFFSET);
                    int size = NumberHelper.intOf(request.getParameter("size"), SIZE);
                    String[] filters=request.getParameter("filters").split(",");
                    for(String filter:filters)
                    copyRaw(generator, query1.query(query, null, offset, size));
                    /*
                    views = this.query.queryByName(query);
                    String filters = request.getParameter("filters");
                    if (filters != null) {
                        views = Arrays.stream(views).filter(view -> {
                            for (String filter : filters.split(",")) {
                                if (AreaView.Level.valueOf(filter.toUpperCase()) == view.level())
                                    return true;
                            }
                            return false;
                        }).toArray(AreaView[]::new);
                    } */
                } else {
                    views = this.query.queryCountry();
                }
                //writeAreaViews(generator, views);
            } else {
                String[] paths = pathInfo.split("/");
                if (paths.length == 2) {//id query
                    ESAreaQuery esAreaQuery = new ESAreaQuery();
                    try (OutputStream os = esAreaQuery.query(Integer.parseInt(paths[1]))) {

                    }
                /*
                AreaView view = query.query(paths[1]);
                if (view != null) {
                    writeAreaView(generator, view);
                } else {
                    writeNotFind(response, generator, paths[1]);
                }*/
                } else if (paths.length > 2 && paths[2].equals("juri")) {
                    AreaView[] views = query.queryByJurisdiction(paths[1]);
                    writeAreaViews(generator, views);
                } else {
                    writeNotFind(response, generator, paths[1]);
                }
            }
        }
    }

    private void copyRaw(JsonGenerator generator, OutputStream os) throws IOException {
        if (os instanceof ByteArrayOutputStream) {
            InputStream is = new ByteArrayInputStream(((ByteArrayOutputStream) os).toByteArray());
            JsonParser parser = JSON_FACTORY.createParser(is);
            while (parser.nextToken() != null) {
                generator.copyCurrentEvent(parser);
            }
        }
    }

    private void writeAreaViews(JsonGenerator generator, AreaView[] views) throws IOException {
        generator.writeStartObject();
        generator.writeArrayFieldStart("areas");
        for (AreaView view : views)
            writeAreaView(generator, view);
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeNotFind(HttpServletResponse resp, JsonGenerator generator, String id) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        generator.writeStartObject();
        generator.writeNumberField("code", 1001);
        generator.writeStringField("message", "Not query area(code=" + id + ")");
        generator.writeEndObject();
    }

    private void writeAreaView(JsonGenerator generator, AreaView view) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("code", view.code());

        generator.writeObjectFieldStart("name");
        generator.writeStringField("name", view.name().name());
        generator.writeStringField("initials", String.valueOf(view.name().initials()));
        //if (Arrays.binarySearch(fields, "abbreviation") >= 0)
        generator.writeStringField("abbreviation", view.name().abbreviation());
        generator.writeStringField("mnemonic", view.name().mnemonic());
        if (view.name().alias() != null && !view.name().alias().isEmpty())
            generator.writeStringField("alias", view.name().alias());
        generator.writeEndObject();

        generator.writeObjectFieldStart("parent");
        generator.writeStringField("code", view.parentAreaView().code());
        generator.writeStringField("name", view.parentAreaView().name());
        generator.writeStringField("abbreviation", view.parentAreaView().abbreviation());
        generator.writeEndObject();


        generator.writeObjectFieldStart("location");
        generator.writeNumberField("longitude", view.location().longitude());
        generator.writeNumberField("latitude", view.location().latitude());
        generator.writeEndObject();


        generator.writeStringField("zipcode", view.zipcode());
        generator.writeStringField("telephoneCode", view.telephoneCode());
        generator.writeStringField("level", view.level().name());
        generator.writeEndObject();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Area area = parserJson(request.getInputStream());
        //areaService.save(area);
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader("location", "/v1/areas/" + area.code());
    }

/*
    private String[] fileds(HttpServletRequest request) {
        String field = request.getParameter("field");
        if (field == null) return FIELDS;
        else {
            String[] temp = field.split(",");
            //排序用于二分查找
            Arrays.sort(temp, String.CASE_INSENSITIVE_ORDER);
            return temp;
        }
    }
 */

    /**
     * @param is
     * @return
     * @throws JsonParseException
     * @throws IOException
     */
    private Area parserJson(InputStream is) throws JsonParseException, IOException {
        JsonParser parser = JSON_FACTORY.createParser(is);
        String code = "", parentCode = "", name = "", abbreviation = "";
        String zipcode = null, alias = null, telephoneCode = null;
        WGS84 wgs84 = null;
        AreaView.Level level = AreaView.Level.COUNTRY;
        /*
         while (jParser.nextToken() != JsonToken.END_OBJECT) {
            String fieldname = jParser.getCurrentName();
            if ("boundy".equals(fieldname)) {
                while (jParser.nextToken() != JsonToken.END_OBJECT) {//JsonToken.END_ARRAY
                    if ("latitude".equals(fieldname)) {
                        jParser.nextToken();
                        latitude = jParser.getDoubleValue();
                    }
                    if ("longitude".equals(fieldname)) {
                        jParser.nextToken();
                        longitude = jParser.getDoubleValue();
                    }
                }
            }
           }
         */
        while (!parser.isClosed()) {
            JsonToken jsonToken = parser.nextToken();
            if (JsonToken.FIELD_NAME.equals(jsonToken)) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                switch (fieldName) {
                    case "code":
                        code = parser.getValueAsString();
                        break;
                    case "parentCode":
                        parentCode = parser.getValueAsString();
                        break;
                    case "name":
                        name = parser.getValueAsString();
                        break;
                    case "abbreviation":
                        abbreviation = parser.getValueAsString();
                        break;
                    case "alias":
                        alias = parser.getValueAsString();
                        break;
                    case "zipcode":
                        zipcode = parser.getValueAsString();
                        break;
                    case "telephoneCode":
                        telephoneCode = parser.getValueAsString();
                        break;
                    case "boundy":
                        //wgs84 = deserialize(parser);
                        break;
                    case "level":
                        level = parser.readValueAs(AreaView.Level.class);
                        break;
                }
            }
        }
        Name areaName = new Name(name, abbreviation, alias);
        return switch (level) {
            case PROVINCE -> new Province(code, parentCode, areaName, wgs84, zipcode, telephoneCode);
            case COUNTRY -> new Country(code, parentCode, areaName, wgs84, zipcode, telephoneCode);
            case CITY -> new City(code, parentCode, areaName, wgs84, zipcode, telephoneCode);
            case COUNTY -> new County(code, parentCode, areaName, wgs84, zipcode, telephoneCode);
            case TOWN -> new Town(code, parentCode, areaName, wgs84, zipcode, telephoneCode);
        };
    }

    private Boundary deserialize(JsonParser parser) throws IOException {
        WGS84[] wgs84s = new WGS84[3];
        double longitude = 0.0, latitude = 0.0;
        int i = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (JsonToken.FIELD_NAME == parser.nextToken()) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                switch (fieldName) {
                    case "longitude":
                        longitude = parser.getValueAsDouble(0.0);
                        break;
                    case "latitude":
                        latitude = parser.getValueAsDouble(0.0);
                        break;
                }
            }
            long zero = Double.doubleToLongBits(0.0);
            if (Double.doubleToLongBits(longitude) != zero && Double.doubleToLongBits(latitude) != zero) {
                wgs84s[i] = new WGS84(longitude, latitude);
                i++;
            }
        }
        return new Boundary(wgs84s[0]);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            String[] paths = pathInfo.split("/");
            if (paths.length == 2) {
                AreaRepository repository = new PsqlAreaRepository();
                repository.delete(paths[1]);
            }
        }
        resp.setContentType("application/json; charset=UTF-8");
        JsonGenerator generator = JSON_FACTORY.createGenerator(resp.getOutputStream(), JsonEncoding.UTF8)
                .setPrettyPrinter(new DefaultPrettyPrinter());
        generator.writeStartObject();
        generator.writeStringField("status", "success");
        generator.writeNumberField("code", 20103);
        generator.writeStringField("message", "area delete success");
        generator.writeEndObject();
        generator.flush();
        generator.close();
    }
}
