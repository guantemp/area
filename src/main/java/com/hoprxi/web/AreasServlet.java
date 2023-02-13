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

package com.hoprxi.web;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.hoprxi.application.AreaQuery;
import com.hoprxi.application.AreaView;
import com.hoprxi.domain.model.Area;
import com.hoprxi.infrastructure.query.PsqlAreaQuery;

import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-11
 *          <p>
 *          restful http<br/>
 *          areas return all country <br/>
 *          areas/code(string) return area where key=area.code,such as:areas/51000
 *          areas/code(string)/juri(jurisdiction) return jurisdiction area where area code,such as:areas/51000/juri
 *          <br/>
 *          <ul>
 *          parameter:
 *          <li>search=regularExpression(name,mnemonic) and filter=country,province,city,county,town</li>
 *          <li>field=name,pinyin,abbreviation, initials,alternativeAbbreviation, boundary, zipcode,telephoneCode</li>
 *          </ul>
 *          </p>
 */
@WebServlet(urlPatterns = {"/v1/areas/*"}, name = "areas", asyncSupported = false, initParams = {
        @WebInitParam(name = "database", value = "arangodb")})
public class AreasServlet extends HttpServlet {
    private static final String[] FIELDS = {"name", "zipcode", "telephoneCode"};
    private static final long serialVersionUID = 1L;

    static {
        Arrays.sort(FIELDS, String.CASE_INSENSITIVE_ORDER);
    }

    private final JsonFactory jasonFactory = JsonFactory.builder().build();
    private AreaQuery query = new PsqlAreaQuery();

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
        JsonGenerator generator = jasonFactory.createGenerator(response.getOutputStream(), JsonEncoding.UTF8);
        generator.useDefaultPrettyPrinter();
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            String search = request.getParameter("search");
            if (search != null) {
                AreaView[] views = query.queryByName(search);
                String filter = request.getParameter("filter");
                if (filter != null) {
                    String upperCaeFilter = filter.toUpperCase();
                    views = Arrays.stream(views).filter(view -> AreaView.Level.valueOf(upperCaeFilter) == view.level()).collect(Collectors.toList()).toArray(new AreaView[0]);
                }
                writeAreas(generator, views);
            } else {
                AreaView[] views = query.queryCountry();
                writeAreas(generator, views);
            }
        } else {
            String[] paths = pathInfo.split("/");
            if (paths.length == 2) {
                AreaView view = query.query(paths[1]);
                if (view != null) {
                    writeAreaView(generator, view);
                } else {
                    writeNotFind(response, generator, paths[1]);
                }
            } else if (paths.length > 2 && paths[2].equals("juri")) {
                AreaView[] views = query.queryByJurisdiction(paths[1]);
                writeAreas(generator, views);
            } else {
                writeNotFind(response, generator, paths[1]);
            }
        }
        generator.flush();
        generator.close();
    }

    private void writeAreas(JsonGenerator generator, AreaView[] views) throws IOException {
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
        generator.writeStringField("message", "Not query area(id=" + id + ")");
        generator.writeEndObject();
    }

    private void writeAreaView(JsonGenerator generator, AreaView view) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("code", view.code());

        generator.writeObjectFieldStart("name");
        generator.writeStringField("name", view.name().name());
        generator.writeStringField("mnemonic", view.name().mnemonic());
        generator.writeStringField("initials", String.valueOf(view.name().initials()));
        generator.writeStringField("abbreviation", view.name().abbreviation());
        if (view.name().alternativeAbbreviation() != null && !view.name().alternativeAbbreviation().isEmpty())
            generator.writeStringField("alternativeAbbreviation", view.name().alternativeAbbreviation());
        generator.writeEndObject();

        generator.writeObjectFieldStart("parent");
        generator.writeStringField("code", view.parentAreaView().code());
        generator.writeStringField("name", view.parentAreaView().name());
        generator.writeEndObject();

        generator.writeArrayFieldStart("boundary");
        generator.writeStartObject();
        generator.writeNumberField("longitude", view.boundary().getCentre().longitude());
        generator.writeNumberField("latitude", view.boundary().getCentre().latitude());
        generator.writeEndObject();
        if (view.boundary().getMin() != null) {
            generator.writeStartObject();
            generator.writeNumberField("longitude", view.boundary().getMin().longitude());
            generator.writeNumberField("latitude", view.boundary().getMin().latitude());
            generator.writeEndObject();
        }
        if (view.boundary().getMax() != null) {
            generator.writeStartObject();
            generator.writeNumberField("longitude", view.boundary().getMax().longitude());
            generator.writeNumberField("latitude", view.boundary().getMax().latitude());
            generator.writeEndObject();
        }
        generator.writeEndArray();

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

    /**
     * @param request
     * @return
     */
    private String[] fileds(HttpServletRequest request) {
        String field = request.getParameter("field");
        if (field == null) return FIELDS;
        else {
            String[] temp = field.split(",");
            Arrays.sort(temp, String.CASE_INSENSITIVE_ORDER);
            return temp;
        }
    }

    /**
     * @param is
     * @return
     * @throws JsonParseException
     * @throws IOException
     */
    private Area parserJson(InputStream is) throws JsonParseException, IOException {
        JsonFactory jfactory = new JsonFactory();
        JsonParser jParser = jfactory.createParser(is);
        int id = 0, parentId = 0;
        //Name name = "";
        String abbreviation = "";
        String postcode = "";
        String pinyin = "";
        String initials = "";
        String mergerAbbreviation = "";
        //Name mergerName = "";
        String remark = "";
        String cityCode = "";
        //Grade grade = null;
        double latitude = 0, longitude = 0;
        byte sort = (byte) 0;
        char firstChar = 0;
        while (jParser.nextToken() != JsonToken.END_OBJECT) {
            String fieldname = jParser.getCurrentName();
            if ("id".equals(fieldname)) {
                jParser.nextToken();
                id = jParser.getIntValue();
            }
            if ("parentId".equals(fieldname)) {
                jParser.nextToken();
                parentId = jParser.getIntValue();
            }
            if ("name".equals(fieldname)) {
                jParser.nextToken();
                // name = jParser.getText();
            }
            if ("mergerName".equals(fieldname)) {
                jParser.nextToken();
                //mergerName = jParser.getText();
            }
            if ("abbreviation".equals(fieldname)) {
                jParser.nextToken();
                abbreviation = jParser.getText();
            }
            if ("pinyin".equals(fieldname)) {
                jParser.nextToken();
                pinyin = jParser.getText();
            }
            if ("initials".equals(fieldname)) {
                jParser.nextToken();
                initials = jParser.getText();
            }
            if ("firstChar".equals(fieldname)) {
                jParser.nextToken();
                firstChar = jParser.getText().charAt(0);
            }
            if ("mergerAbbreviation".equals(fieldname)) {
                jParser.nextToken();
                mergerAbbreviation = jParser.getText();
            }
            if ("zipcode".equals(fieldname)) {
                jParser.nextToken();
                postcode = jParser.getText();
            }
            if ("cityCode".equals(fieldname)) {
                jParser.nextToken();
                cityCode = jParser.getText();
            }
            if ("grade".equals(fieldname)) {
                jParser.nextToken();
                //grade = Grade.valueOf(jParser.getText());
            }
            if ("sort".equals(fieldname)) {
                jParser.nextToken();
                sort = jParser.getByteValue();
            }
            if ("coordinate".equals(fieldname)) {
                while (jParser.nextToken() != JsonToken.END_OBJECT) {
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
            if ("remark".equals(fieldname)) {
                jParser.nextToken();
                remark = jParser.getText();
            }
        }
        return null;
        //return new Area(id, parentId, name, mergerName, abbreviation, mergerAbbreviation, pinyin, initials, firstChar,
        //       grade, cityCode, zipcode, new WGS84(latitude, longitude), sort, remark);
    }

    /**
     * @param request
     * @return
     */
    private String sortBy(HttpServletRequest request) {
        String sort = request.getParameter("sort");
        if (null != sort) return sort.split(",")[0];
        return null;
    }

    /**
     * @param j
     * @param area
     * @param fields
     * @throws JsonGenerationException
     * @throws IOException
     */
    private void writeArea(JsonGenerator j, Area area, String[] fields) throws JsonGenerationException, IOException {
        j.writeStartObject();
        /*
        j.writeNumberField("id", area.id());
        j.writeNumberField("parentId", area.parentId());
        j.writeStringField("name", area.name());
        if (Arrays.binarySearch(fields, "mergerName") > 0)
            j.writeStringField("mergerName", area.mergerName());
        if (Arrays.binarySearch(fields, "abbreviation") >= 0)
            j.writeStringField("abbreviation", area.abbreviation());
        if (Arrays.binarySearch(fields, "mergerAbbreviation") >= 0)
            j.writeStringField("mergerAbbreviation", area.mergerAbbreviation());
        if (Arrays.binarySearch(fields, "pinyin") >= 0)
            j.writeStringField("pinyin", area.pinyin());
        if (Arrays.binarySearch(fields, "initials") >= 0)
            j.writeStringField("initials", area.initials());
        if (Arrays.binarySearch(fields, "firstChar") >= 0)
            j.writeStringField("firstChar", String.valueOf(area.firstChar()));
        j.writeStringField("grade", area.grade().name());
        if (Arrays.binarySearch(fields, "coordinate") >= 0) {
            j.writeObjectFieldStart("coordinate");
            j.writeNumberField("latitude", area.wgs84().latitude());
            j.writeNumberField("longitude", area.wgs84().longitude());
            j.writeEndObject();
        }
        if (Arrays.binarySearch(fields, "zipcode") >= 0)
            j.writeStringField("zipcode", area.zipcode());
        if (Arrays.binarySearch(fields, "cityCode") >= 0)
            j.writeStringField("cityCode", area.cityCode());
        if (Arrays.binarySearch(fields, "sort") >= 0)
            j.writeNumberField("sort", area.sort());
        if (Arrays.binarySearch(fields, "remark") >= 0)
            j.writeStringField("remark", area.remark());
        j.writeEndObject();

         */
    }

    /**
     * @param os
     * @param area
     * @throws JsonGenerationException
     * @throws IOException
     */
    private void writeArea(OutputStream os, Area area, String[] fields) throws JsonGenerationException, IOException {
        JsonFactory jasonFactory = new JsonFactory();
        JsonGenerator j = jasonFactory.createGenerator(os, JsonEncoding.UTF8);
        j.setPrettyPrinter(new DefaultPrettyPrinter());
        writeArea(j, area, fields);
        j.flush();
        j.close();
    }

    /**
     * @param os
     * @param areas
     * @throws JsonGenerationException
     * @throws IOException
     */
    private void writeAreas(OutputStream os, Collection<Area> areas, String[] fields) throws JsonGenerationException, IOException {
        // for(String field:fields)
        // System.out.println(field);
        JsonFactory jasonFactory = new JsonFactory();
        JsonGenerator j = jasonFactory.createGenerator(os, JsonEncoding.UTF8);
        j.setPrettyPrinter(new DefaultPrettyPrinter());
        j.writeStartObject();
        j.writeArrayFieldStart("area");
        for (Area area : areas)
            writeArea(j, area, fields);
        j.writeEndArray();
        j.writeEndObject();
        j.flush();
        j.close();
    }
}
