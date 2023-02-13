package com.hoprxi.domain.model;

import com.hoprxi.domain.model.coordinate.Boundary;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.persistence.PsqlAreaRepository;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public class AreaRepositoryTest {

    private static final AreaRepository repository = new PsqlAreaRepository();

    @BeforeClass
    public void beforeClass() {
        Name name = new Name("中华人民共和国", "中国");
        Boundary boundary = new Boundary(new WGS84(116.405289, 39.904987));
        Country china = new Country("156", "156", name, boundary);
        repository.save(china);

        name = new Name("四川省", "川", "蜀");
        boundary = new Boundary(new WGS84(104.065735, 30.659462));
        Province sichuan = new Province("510000", "156", name, boundary);
        repository.save(sichuan);
        name = new Name("乐山市","乐山");
        boundary = new Boundary(new WGS84(105.435226, 28.897572));
        City leshan = new City("511100", "510000", name, boundary, "614000", "0833");
        repository.save(leshan);
        name = new Name("南充市","南充");
        boundary = new Boundary(new WGS84(106.082977, 30.79528));
        City nanchong = new City("511300", "510000", name, boundary, "646000", "0830");
        repository.save(nanchong);

        name = new Name("泸州市", "泸");
        boundary = new Boundary(new WGS84(105.443352, 28.889137));
        City luzhou = new City("510500", "510000", name, boundary, "637000", "0817");
        repository.save(luzhou);
        name = new Name("龙马潭区","龙马潭");
        boundary = new Boundary(new WGS84(105.435226, 28.897572));
        County longmatai = new County("510504", "510500", name, boundary, "637000", "0817");
        repository.save(longmatai);
        name = new Name("小市街道","小市");
        boundary = new Boundary(new WGS84(105.44738, 28.900055));
        Town xiaoshi = new Town("510504001", "510504", name, boundary);
        repository.save(xiaoshi);
        name =  new Name("石洞镇","石洞");
        boundary = new Boundary(new WGS84(105.453644, 28.993362));
        Town sidong = new Town("510504102", "510504", name, boundary);
        repository.save(sidong);
        name =  new Name("江阳区","江阳");
        boundary = new Boundary(new WGS84(105.445129, 28.882889));
        County jiangyan = new County("510502", "510500", name, boundary);
        repository.save(jiangyan);
        name =  new Name("叙永县","叙永");
        boundary = new Boundary(new WGS84(105.437775, 28.167919));
        County xuyong = new County("510524", "510500", name, boundary);
        repository.save(xuyong);


        name = new Name("云南省", "滇", "云");
        boundary = new Boundary(new WGS84(102.71225, 25.040609));
        Province yunnan = new Province("530000", "156", name, boundary);
        repository.save(yunnan);
    }

    @Test(invocationCount = 10)
    public void testFind() {
        Area area = repository.find("156");
        System.out.println(area);
    }

    @Test
    public void testSave() {

    }

    @Test
    public void testDelete() {
    }
}