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

import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.CollectionType;
import com.arangodb.entity.EdgeDefinition;
import com.arangodb.entity.KeyType;
import com.arangodb.model.CollectionCreateOptions;
import com.arangodb.model.SkiplistIndexOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 2019-05-14
 */

public class AreaSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaSetup.class);

    public static void setup(String databaseName) {
        ArangoDB arangoDB = ArangoDBUtil.getResource();
        if (arangoDB.db(databaseName).exists()) {
            arangoDB.db(databaseName).drop();
            LOGGER.info("{} has exists,will be drop!", databaseName);
        }
        arangoDB.createDatabase(databaseName);
        //vertex
        for (String s : new String[]{"country", "province", "city", "county", "town", "wgs84"}) {
            CollectionCreateOptions options = new CollectionCreateOptions();
            options.keyOptions(true, KeyType.traditional, 1, 1);
            arangoDB.db(databaseName).createCollection(s, options);
        }
        ArangoDatabase arangoDatabase = arangoDB.db(databaseName);
        //index
        Collection<String> index = new ArrayList<>();
        index.add("name.initials");
        index.add("name.name");
        index.add("name.pinyin");
        SkiplistIndexOptions skiplistIndexOptions = new SkiplistIndexOptions().unique(false).sparse(true);
        for (String s : new String[]{"country", "province", "city", "county", "town"})
            arangoDatabase.collection(s).ensureSkiplistIndex(index, skiplistIndexOptions);

        //edge
        for (String s : new String[]{"jurisdiction", "location"}) {
            CollectionCreateOptions options = new CollectionCreateOptions().type(CollectionType.EDGES);
            arangoDatabase.createCollection(s, options);
        }
        //graph
        Collection<EdgeDefinition> edgeList = new ArrayList<>();
        edgeList.add(new EdgeDefinition().collection("location").from("country", "province", "city", "county", "town").to("wgs84"));
        edgeList.add(new EdgeDefinition().collection("jurisdiction").from("country", "province", "city", "county").to("province", "city", "county", "town"));
        arangoDatabase.createGraph("area", edgeList);
        arangoDB.shutdown();
        LOGGER.info("{} create success!", databaseName);
        arangoDB = null;
    }
}
