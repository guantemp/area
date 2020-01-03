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

import area.hoprxi.core.domain.model.*;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-28
 */
public class ArangoDBCountyRepositoryTest {
    private static CountyRepository repository = new ArangoDBCountyRepository();
    private static CityRepository cityRepository = new ArangoDBCityRepository();

    @BeforeClass
    public static void setUpBeforeClass() {
        Name name = new Name("泸州市", "泸州", null, null);
        City luzhou = new City("510500", "510000", name, "614000", (byte) 4, new WGS84(105.443352, 28.889137));
        cityRepository.save(luzhou);

        name = new Name("龙马潭区", "龙马潭", null, null);
        County longmatai = new County("510504", "510500", name, "614000", (byte) 75, new WGS84(105.435226, 28.897572));
        repository.save(longmatai);
        name = new Name("江阳区", "江阳", null, null);
        County jiangyan = new County("510502", "510500", name, "614000", (byte) 23, new WGS84(105.445129, 28.882889));
        repository.save(jiangyan);
        name = new Name("叙永县", "叙永", null, null);
        County xuyong = new County("510524", "510500", name, "614000", (byte) 35, new WGS84(105.437775, 28.167919));
        repository.save(xuyong);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        //repository.remove("156");
        //repository.remove("840");
    }

    @Test
    public void test() {
        County longmatai = repository.find("510504");
        Assert.assertNotNull(longmatai);
        longmatai = repository.find("520504");
        Assert.assertNull(longmatai);
        County[] counties = repository.findByJurisdiction("510500");
        Assert.assertEquals(3, counties.length);
        Assert.assertFalse(repository.hasTown("510524"));
        Assert.assertTrue(cityRepository.hasCounty("510500"));
    }
}