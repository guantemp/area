/*
 * Copyright (c) 2019. www.foxtail.cc rights Reserved.
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

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.Protocol;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocypack.module.jdk8.VPackJdk8Module;

import java.lang.reflect.Array;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 2019-07-24
 */
public final class ArangoDBUtil {

    public static ArangoDB getResource() {
        ArangoDB.Builder builder = new ArangoDB.Builder();
        builder.useProtocol(Protocol.VST).host("125.68.186.195", 8529);
        builder.registerModule(new VPackJdk8Module()).user("root").password("Qwe123465");
        ArangoDB arangoDB = builder.build();
        return arangoDB;
    }

    /**
     * @param arangoDatabase
     * @param t
     * @param offset
     * @param limit
     * @param <T>
     * @return
     */
    public static <T> T[] calculationCollectionSize(ArangoDatabase arangoDatabase, Class<T> t, long offset, int limit) {
        if (offset < 0l)
            offset = 0l;
        if (limit < 0)
            limit = 0;
        long count = 0;
        final String countQuery = " RETURN LENGTH(" + t.getSimpleName().toLowerCase() + ")";
        final ArangoCursor<VPackSlice> countCursor = arangoDatabase.query(countQuery, null, null, VPackSlice.class);
        for (; countCursor.hasNext(); ) {
            count = countCursor.next().getAsLong();
        }

        int difference = (int) (count - offset);
        if (difference <= 0)
            return (T[]) Array.newInstance(t, 0);
        int capacity = difference >= limit ? limit : difference;
        return (T[]) Array.newInstance(t, capacity);
    }
}
