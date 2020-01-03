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
public class ArangoDBProvinceRepositoryTest {
    private static ProvinceRepository repository = new ArangoDBProvinceRepository();
    private static CountryRepository countryRepository = new ArangoDBCountryRepository();

    @BeforeClass
    public static void setUpBeforeClass() {
        Name name = new Name("中华人民共和国", "中国", null, null);
        Country china = new Country("156", "156", name, "000000", (byte) 1, new WGS84(116.405289, 39.904987));
        countryRepository.save(china);

        name = new Name("四川省", "四川", "川", "蜀");
        Province sichuan = new Province("510000", "156", name, "610000", (byte) 23, new WGS84(104.065735, 30.659462));
        repository.save(sichuan);
        name = new Name("云南省", "云南", "滇", null);
        Province yunnan = new Province("530000", "156", name, "620000", (byte) 25, new WGS84(102.71225, 25.040609));
        repository.save(yunnan);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        //countryRepository.remove("156");

        //repository.remove("510000");
        //repository.remove("530000");
    }

    @Test
    public void test() {
        Province sichuan = repository.find("510000");
        Assert.assertNotNull(sichuan);
        sichuan = repository.find("510001");
        Assert.assertNull(sichuan);
        Province[] provinces = repository.findByJurisdiction("156");
        Assert.assertEquals(2, provinces.length);
        Assert.assertFalse(repository.hasCity("510000"));
    }
}