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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-27
 */
public class ArangoDBCountryRepositoryTest {
    private static CountryRepository repository = new ArangoDBCountryRepository();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        Name name = new Name("中华人民共和国", "中国", null, null);
        Country china = new Country("156", "156", name, "000000", (byte) 1, new WGS84(116.405289, 39.904987));
        repository.save(china);
        name = new Name("美利坚合众国", "美国", null, null);
        Country usd = new Country("840", "840", name, "100000", (byte) 10, new WGS84(16.405289, 319.904987));
        repository.save(usd);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        //repository.remove("156");
        //repository.remove("840");
    }

    @Test
    public void test() {
        Country country = repository.find("156");
        Assert.assertNotNull(country);
        country = repository.find("840");
        Assert.assertNotNull(country);
        Assert.assertFalse(repository.hasProvince("840"));
        country = repository.find("841");
        Assert.assertNull(country);
        Country[] countries = repository.findAll();
        Assert.assertEquals(2, countries.length);
    }
}