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

import area.hoprxi.core.domain.model.Country;
import area.hoprxi.core.domain.model.CountryRepository;
import area.hoprxi.core.domain.model.Name;
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
 * @version 0.0.1 2019-12-27
 */
public class ArangoDBCountryRepository implements CountryRepository {
    private static final VertexUpdateOptions UPDATE_OPTIONS = new VertexUpdateOptions().keepNull(false);

    private ArangoDatabase area = ArangoDBUtil.getResource().db("area");

    @Override
    public Country find(String id) {
        boolean exists = area.collection("country").documentExists(id);
        if (exists) {
            final String query = "WITH country,wgs84\n" +
                    "FOR c IN country FILTER c._key == @id\n" +
                    "FOR w IN 1..1 OUTBOUND c._id location\n" +
                    "RETURN {'id':c._key,'name':c.name,'postcode':c.postcode,'sequence':c.sequence,'wgs84':w}";
            final Map<String, Object> bindVars = new MapBuilder().put("id", id).get();
            ArangoCursor<VPackSlice> slices = area.query(query, bindVars, null, VPackSlice.class);
            return rebuild(slices.next());
        }
        return null;
    }

    private Country rebuild(VPackSlice slice) {
        String id = slice.get("id").getAsString();
        String parentId = id;
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
        return new Country(id, parentId, name, postcode, sequence, wgs84);
    }

    @Override
    public void save(Country country) {
        boolean exists = area.collection("country").documentExists(country.id());
        ArangoGraph graph = area.graph("area");
        if (exists) {
            VertexUpdateEntity countryVertex = graph.vertexCollection("country").updateVertex(country.id(), country, UPDATE_OPTIONS);
            final String query = "WITH country,wgs84\n" +
                    "FOR v,e IN 1..1 OUTBOUND @startVertex location REMOVE v IN wgs84 REMOVE e IN location";
            final Map<String, Object> bindVars = new MapBuilder().put("startVertex", countryVertex.getId()).get();
            area.query(query, bindVars, null, VertexEntity.class);
            insertLocationEdge(graph, countryVertex, country.wgs84());
        } else {
            VertexEntity countryVertex = graph.vertexCollection("country").insertVertex(country);
            insertLocationEdge(graph, countryVertex, country.wgs84());
        }
    }

    private void insertLocationEdge(ArangoGraph graph, DocumentEntity countryVertex, WGS84 wgs84) {
        VertexEntity wgs84Vertex = graph.vertexCollection("wgs84").insertVertex(wgs84);
        graph.edgeCollection("location").insertEdge(new LocationEdge(countryVertex.getId(), wgs84Vertex.getId()));
    }

    @Override
    public void remove(String id) {
        final String query = "WITH country,wgs84\n" +
                "FOR v,e IN 1..1 OUTBOUND @startVertex location REMOVE v IN wgs84 REMOVE e IN location";
        final Map<String, Object> bindVars = new MapBuilder().put("startVertex", "country/" + id).get();
        area.query(query, bindVars, null, VertexEntity.class);
        area.graph("area").vertexCollection("country").deleteVertex(id);
    }

    @Override
    public boolean hasProvince(String id) {
        final String query = "WITH country,wgs84\n" +
                "FOR v IN 1..1 OUTBOUND @startVertex jurisdiction RETURN v";
        final Map<String, Object> bindVars = new MapBuilder().put("startVertex", "country/" + id).get();
        ArangoCursor<VPackSlice> result = area.query(query, bindVars, null, VPackSlice.class);
        return result.hasNext();
    }

    @Override
    public Country[] findAll() {
        final String query = "WITH country,wgs84\n" +
                "FOR c IN country\n" +
                "FOR w IN 1..1 OUTBOUND c._id location\n" +
                "RETURN {'id':c._key,'name':c.name,'postcode':c.postcode,'sequence':c.sequence,'wgs84':w}";
        ArangoCursor<VPackSlice> slices = area.query(query, null, null, VPackSlice.class);
        List<Country> countryList = new ArrayList<>();
        while (slices.hasNext()) {
            countryList.add(rebuild(slices.next()));
        }
        return countryList.toArray(new Country[countryList.size()]);
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
