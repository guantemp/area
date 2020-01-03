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

package area.hoprxi.core.infrastructure.persistence;

import area.hoprxi.core.domain.model.Name;
import area.hoprxi.core.domain.model.Town;
import area.hoprxi.core.domain.model.TownRepository;
import area.hoprxi.core.domain.model.WGS84;
import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDatabase;
import com.arangodb.ArangoGraph;
import com.arangodb.entity.DocumentEntity;
import com.arangodb.entity.DocumentField;
import com.arangodb.entity.VertexEntity;
import com.arangodb.entity.VertexUpdateEntity;
import com.arangodb.model.VertexUpdateOptions;
import com.arangodb.util.MapBuilder;
import com.arangodb.velocypack.VPackSlice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-28
 */
public class ArangoDBTownRepository implements TownRepository {
    private static final VertexUpdateOptions UPDATE_OPTIONS = new VertexUpdateOptions().keepNull(false);
    private ArangoDatabase area = ArangoDBUtil.getResource().db("area");

    @Override
    public Town find(String id) {
        boolean exists = area.collection("town").documentExists(id);
        if (exists) {
            final String query = "WITH county,town,wgs84\n" +
                    "FOR t IN town FILTER t._key == @id\n" +
                    "FOR w IN 1..1 OUTBOUND t._id location\n" +
                    "FOR c IN 1..1 INBOUND t._id jurisdiction\n" +
                    "RETURN {'id':t._key,'parentId':c._key,'name':t.name,'postcode':t.postcode,'sequence':t.sequence,'wgs84':w}";
            final Map<String, Object> bindVars = new MapBuilder().put("id", id).get();
            ArangoCursor<VPackSlice> slices = area.query(query, bindVars, null, VPackSlice.class);
            return rebuild(slices.next());
        }
        return null;
    }

    @Override
    public Town[] findByJurisdiction(String countyId) {
        final String query = "WITH county,town,wgs84\n" +
                "FOR c IN county FILTER c._key == @id\n" +
                "FOR t IN 1..1 OUTBOUND c._id jurisdiction\n" +
                "FOR w IN 1..1 OUTBOUND t._id location\n" +
                "RETURN {'id':t._key,'parentId':c._key,'name':t.name,'postcode':t.postcode,'sequence':t.sequence,'wgs84':w}";
        final Map<String, Object> bindVars = new MapBuilder().put("id", countyId).get();
        ArangoCursor<VPackSlice> slices = area.query(query, bindVars, null, VPackSlice.class);
        List<Town> townList = new ArrayList<>();
        while (slices.hasNext()) {
            townList.add(rebuild(slices.next()));
        }
        return townList.toArray(new Town[townList.size()]);
    }

    private Town rebuild(VPackSlice slice) {
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
        Name name = new Name(aName, pinyin, initials, mergerName, alternative, mergerAlternative);

        String postcode = slice.get("postcode").getAsString();
        byte sequence = slice.get("sequence").getAsByte();
        //WGS84
        VPackSlice wgs84Slice = slice.get("wgs84");
        double latitude = wgs84Slice.get("latitude").getAsDouble();
        double longitude = wgs84Slice.get("longitude").getAsDouble();
        WGS84 wgs84 = new WGS84(latitude, longitude);
        return new Town(id, parentId, name, postcode, sequence, wgs84);
    }

    @Override
    public void remove(String id) {
        final String query = "WITH town,wgs84\n" +
                "FOR v,e IN 1..1 OUTBOUND @startVertex location REMOVE v IN wgs84 REMOVE e IN location";
        final Map<String, Object> bindVars = new MapBuilder().put("startVertex", "town/" + id).get();
        area.query(query, bindVars, null, VertexEntity.class);
        area.graph("area").vertexCollection("town").deleteVertex(id);
    }

    @Override
    public void save(Town town) {
        boolean exists = area.collection("town").documentExists(town.id());
        ArangoGraph graph = area.graph("area");
        if (exists) {
            VertexUpdateEntity vertex = graph.vertexCollection("town").updateVertex(town.id(), town, UPDATE_OPTIONS);
            final String query = "WITH town,wgs84\n" +
                    "FOR v,e IN 1..1 OUTBOUND @startVertex location REMOVE v IN wgs84 REMOVE e IN location";
            final Map<String, Object> bindVars = new MapBuilder().put("startVertex", vertex.getId()).get();
            area.query(query, bindVars, null, VertexEntity.class);
            insertLocationEdge(graph, vertex, town.wgs84());
        } else {
            VertexEntity vertex = graph.vertexCollection("town").insertVertex(town);
            insertLocationEdge(graph, vertex, town.wgs84());
            insertJurisdictionEdge(graph, town.parentId(), vertex);
        }
    }

    private void insertJurisdictionEdge(ArangoGraph graph, String parentId, DocumentEntity vertex) {
        VertexEntity countyVertx = graph.vertexCollection("county").getVertex(parentId, VertexEntity.class);
        graph.edgeCollection("jurisdiction").insertEdge(new JurisdictionEdge(countyVertx.getId(), vertex.getId()));
    }

    private void insertLocationEdge(ArangoGraph graph, DocumentEntity vertex, WGS84 wgs84) {
        VertexEntity wgs84Vertex = graph.vertexCollection("wgs84").insertVertex(wgs84);
        graph.edgeCollection("location").insertEdge(new LocationEdge(vertex.getId(), wgs84Vertex.getId()));
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
}
