/*
 * Copyright (c) 2019. www.hoprxi.com rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package area.hoprxi.web.servlet;

import area.hoprxi.core.application.AreaService;
import area.hoprxi.core.domain.model.Area;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import mi.hoprxi.util.NumberHelper;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2019-12-29
 *          <p>3
 *
 *          restful http<br/>
 *          areas return all country <br/>
 *          areas/key(int) return area where key=area.key,such as:areas/51000
 *          <br/>
 *          <ul>
 *          parameter:
 *          <li>q=sub is current area</li>
 *          <li>sort=initials or initials,asc arrange in ascending order</li>
 *          <li>sort=initials,desc Arrange in descending order</li>
 *          <li>field=mergerName,pinyin,abbreviation, initials, firstChar,
 *          mergerAbbreviation, coordinate, postcode,cityCode, sort, remark</li>
 *          </ul>
 *          </p>
 */
@WebServlet(urlPatterns = {"/v1/areas/*"}, name = "areas", asyncSupported = false)
public class AreasServlet extends HttpServlet {
    private static final String[] FIELDS = {"name", "postcode", "cityCode", "sequence"};
    private static final long serialVersionUID = 1L;

    static {
        Arrays.sort(FIELDS, String.CASE_INSENSITIVE_ORDER);
    }

    private AreaService areaService = new AreaService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        response.setContentType("application/json;charset=UTF-8");
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
        if (pathInfo == null) {
            JsonFactory jasonFactory = new JsonFactory();
            JsonGenerator j = jasonFactory.createGenerator(response.getOutputStream(), JsonEncoding.UTF8);
            j.setPrettyPrinter(new DefaultPrettyPrinter());
            Collection<Area> areas = null;//areaService.country();
            writeAreas(response.getOutputStream(), areas, fields);
        } else {
            int id = NumberHelper.intOf(pathInfo);
            if ("sub".equalsIgnoreCase(request.getParameter("q"))) {
                Collection<Area> areas = null;//areaService.findSub(id);
                if (!areas.isEmpty())
                    writeAreas(response.getOutputStream(), areas, fields);
            } else {
                Area area = null;//areaService.find(id);
                if (null != area)
                    writeArea(response.getOutputStream(), area, fields);
                else
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Area area = parserJson(request.getInputStream());
        //areaService.save(area);
        response.setStatus(HttpServletResponse.SC_CREATED);
        response.setHeader("location", "/v1/areas/" + area.id());
    }

    /**
     * @param request
     * @return
     */
    private String[] fileds(HttpServletRequest request) {
        String field = request.getParameter("field");
        if (field == null)
            return FIELDS;
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
            if ("postcode".equals(fieldname)) {
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
         //       grade, cityCode, postcode, new WGS84(latitude, longitude), sort, remark);
    }

    /**
     * @param request
     * @return
     */
    private String sortBy(HttpServletRequest request) {
        String sort = request.getParameter("sort");
        if (null != sort)
            return sort.split(",")[0];
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
        if (Arrays.binarySearch(fields, "postcode") >= 0)
            j.writeStringField("postcode", area.postcode());
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
    private void writeAreas(OutputStream os, Collection<Area> areas, String[] fields)
            throws JsonGenerationException, IOException {
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
