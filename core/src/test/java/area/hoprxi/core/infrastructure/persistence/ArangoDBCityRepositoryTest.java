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
public class ArangoDBCityRepositoryTest {
    private static ProvinceRepository provinceRepository = new ArangoDBProvinceRepository();
    private static CityRepository repository = new ArangoDBCityRepository();

    @BeforeClass
    public static void setUpBeforeClass() {
        Name name = new Name("四川省", "四川", "川", "蜀");
        Province sichuan = new Province("510000", "156", name, "610000", (byte) 23, new WGS84(104.065735, 30.659462));
        provinceRepository.save(sichuan);

        name = new Name("泸州市", "泸州", null, null);
        City luzhou = new City("510500", "510000", name, "614000", (byte) 4, new WGS84(105.443352, 28.889137));
        repository.save(luzhou);
        name = new Name("乐山市", "乐山", null, null);
        City leshan = new City("511100", "510000", name, "614000", (byte) 50, new WGS84(105.435226, 28.897572));
        repository.save(leshan);
        name = new Name("南充市", "南充", null, null);
        City nanchong = new City("511300", "510000", name, "614000", (byte) 50, new WGS84(106.082977, 30.79528));
        repository.save(nanchong);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        //repository.remove("156");
        //repository.remove("840");
    }

    @Test
    public void test() {
        City luzhou = repository.find("510500");
        Assert.assertNotNull(luzhou);
        City leshan = repository.find("511100");
        Assert.assertNotNull(leshan);
        City[] cities = repository.findByJurisdiction("510000");
        Assert.assertEquals(3, cities.length);
        Assert.assertFalse(repository.hasCounty("511300"));
    }
}