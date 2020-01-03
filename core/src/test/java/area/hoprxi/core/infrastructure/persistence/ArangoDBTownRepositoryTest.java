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
public class ArangoDBTownRepositoryTest {
    private static CountyRepository countRepository = new ArangoDBCountyRepository();
    private static TownRepository townRepository = new ArangoDBTownRepository();

    @BeforeClass
    public static void setUpBeforeClass() {
        Name name = new Name("龙马潭区", "龙马潭", null, null);
        County longmatai = new County("510504", "510500", name, "614000", (byte) 75, new WGS84(105.435226, 28.897572));
        countRepository.save(longmatai);

        name = new Name("小市街道", "小市街道", null, null);
        Town xiaoshi = new Town("510504001", "510504", name, "614000", (byte) 15, new WGS84(105.44738, 28.900055));
        townRepository.save(xiaoshi);
        name = new Name("石洞镇", "石洞", null, null);
        Town sidong = new Town("510504102", "510504", name, "614000", (byte) 90, new WGS84(105.453644, 28.993362));
        townRepository.save(sidong);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        //repository.remove("156");
        //repository.remove("840");
    }

    @Test
    public void test() {
        Town xiaoshi = townRepository.find("510504001");
        Assert.assertNotNull(xiaoshi);
        Town sidong = townRepository.find("510504002");
        Assert.assertNull(sidong);
        Town[] towns = townRepository.findByJurisdiction("510504");
        Assert.assertEquals(2, towns.length);
    }

}