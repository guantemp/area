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

package com.hoprxi.infrastructure.persistence;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-27
 */
public class ArangoDBProvinceRepository {
    /*
    private static final VertexUpdateOptions UPDATE_OPTIONS = new VertexUpdateOptions().keepNull(false);
    private ArangoDatabase area = ArangoDBUtil.getResource().db("area");

    @Override
    public Province find(String id) {
        boolean exists = area.collection("province").documentExists(id);
        if (exists) {
            final String query = "WITH country,province,wgs84\n" +
                    "FOR p IN province FILTER p._key == @id\n" +
                    "FOR w IN 1..1 OUTBOUND p._id location\n" +
                    "FOR c IN 1..1 INBOUND p._id jurisdiction\n" +
                    "RETURN {'id':p._key,'parentId':c._key,'name':p.name,'zipcode':p.zipcode,'sequence':p.sequence,'wgs84':w}";
            final Map<String, Object> bindVars = new MapBuilder().put("id", id).get();
            ArangoCursor<VPackSlice> slices = area.query(query, bindVars, null, VPackSlice.class);
            return rebuild(slices.next());
        }
        return null;
    }

    private Province rebuild(VPackSlice slice) {
        String id = slice.get("id").getAsString();
        String parentId = slice.get("parentId").getAsString();
        //Name class
        VPackSlice nameSlice = slice.get("name");
        String aName = nameSlice.get("name").getAsString();
        String pinyin = nameSlice.get("pinyin").getAsString();
        char initials = nameSlice.get("initials").getAsChar();
        String mergerName = nameSlice.get("mergerName").getAsString();
        String alternative = null;
        if (!nameSlice.get("alternative").isNone())
            alternative = nameSlice.get("alternative").getAsString();
        String mergerAlternative = null;
        if (!nameSlice.get("mergerAlternative").isNone())
            mergerAlternative = nameSlice.get("mergerAlternative").getAsString();
        Name name = new Name(aName, pinyin, initials, alternative, mergerAlternative);

        String postcode = slice.get("zipcode").getAsString();
        byte sequence = slice.get("sequence").getAsByte();
        //WGS84
        VPackSlice wgs84Slice = slice.get("wgs84");
        double latitude = wgs84Slice.get("latitude").getAsDouble();
        double longitude = wgs84Slice.get("longitude").getAsDouble();
        String wgs84 = new WGS84(latitude, longitude);
        return new Province(id, parentId, name, postcode, sequence, wgs84);
    }

    @Override
    public Province[] findByJurisdiction(String countryId) {
        final String query = "WITH country,province,wgs84\n" +
                "FOR c IN country FILTER c._key == @id\n" +
                "FOR p IN 1..1 OUTBOUND c._id jurisdiction\n" +
                "FOR w IN 1..1 OUTBOUND p._id location\n" +
                "RETURN {'id':p._key,'parentId':c._key,'name':p.name,'zipcode':p.zipcode,'sequence':p.sequence,'wgs84':w}";
        final Map<String, Object> bindVars = new MapBuilder().put("id", countryId).get();
        ArangoCursor<VPackSlice> slices = area.query(query, bindVars, null, VPackSlice.class);
        List<Province> provinceList = new ArrayList<>();
        while (slices.hasNext()) {
            provinceList.add(rebuild(slices.next()));
        }
        return provinceList.toArray(new Province[provinceList.size()]);
    }

    @Override
    public void remove(String id) {
        final String query = "WITH province,wgs84\n" +
                "FOR v,e IN 1..1 OUTBOUND @startVertex location REMOVE v IN wgs84 REMOVE e IN location";
        final Map<String, Object> bindVars = new MapBuilder().put("startVertex", "province/" + id).get();
        area.query(query, bindVars, null, VertexEntity.class);
        area.graph("area").vertexCollection("province").deleteVertex(id);
    }

    @Override
    public void save(Province province) {
        boolean exists = area.collection("province").documentExists(province.id());
        ArangoGraph graph = area.graph("area");
        if (exists) {
            VertexUpdateEntity vertex = graph.vertexCollection("province").updateVertex(province.id(), province, UPDATE_OPTIONS);
            final String query = "WITH province,wgs84\n" +
                    "FOR v,e IN 1..1 OUTBOUND @startVertex location REMOVE v IN wgs84 REMOVE e IN location";
            final Map<String, Object> bindVars = new MapBuilder().put("startVertex", vertex.getId()).get();
            area.query(query, bindVars, null, VertexEntity.class);
            insertLocationEdge(graph, vertex, province.wgs84());
        } else {
            VertexEntity vertex = graph.vertexCollection("province").insertVertex(province);
            insertLocationEdge(graph, vertex, province.wgs84());
            insertJurisdictionEdge(graph, province.parentId(), vertex);
        }
    }

    private void insertJurisdictionEdge(ArangoGraph graph, String parentId, DocumentEntity countryVertex) {
        VertexEntity countryVertx = graph.vertexCollection("country").getVertex(parentId, VertexEntity.class);
        graph.edgeCollection("jurisdiction").insertEdge(new JurisdictionEdge(countryVertx.getId(), countryVertex.getId()));
    }

    private void insertLocationEdge(ArangoGraph graph, DocumentEntity vertex, WGS84 wgs84) {
        VertexEntity wgs84Vertex = graph.vertexCollection("wgs84").insertVertex(wgs84);
        graph.edgeCollection("location").insertEdge(new LocationEdge(vertex.getId(), wgs84Vertex.getId()));
    }

    @Override
    public boolean hasCity(String id) {
        final String query = "WITH province,wgs84\n" +
                "FOR v IN 1..1 OUTBOUND @startVertex jurisdiction RETURN v";
        final Map<String, Object> bindVars = new MapBuilder().put("startVertex", "province/" + id).get();
        ArangoCursor<VPackSlice> result = area.query(query, bindVars, null, VPackSlice.class);
        return result.hasNext();
    }

    private static class JurisdictionEdge {
        @DocumentField(DocumentField.Type.FROM)
        private String from;

        @DocumentField(DocumentField.Type.TO)
        private String to;

        public JurisdictionEdge(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class LocationEdge {
        @DocumentField(DocumentField.Type.FROM)
        private String from;

        @DocumentField(DocumentField.Type.TO)
        private String to;

        public LocationEdge(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
     */
}
