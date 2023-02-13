package com.hoprxi.application;

import com.hoprxi.infrastructure.query.PsqlAreaQuery;
import org.testng.Assert;
import org.testng.annotations.Test;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-12
 */
public class AreaQueryTest {

    private static final AreaQuery query = new PsqlAreaQuery();

    @Test
    public void testQueryByName() {
        AreaView[] views = query.queryByName("乐山");
        Assert.assertEquals(views.length, 1);
        views = query.queryByName("^ls");
        Assert.assertEquals(views.length, 1);
        views = query.queryByName("小");
        Assert.assertEquals(views.length, 1);

    }

    @Test(invocationCount = 8, threadPoolSize = 4)
    public void testQuery() {
        System.out.println("this is thread" + Thread.currentThread().getId());
        AreaView view = query.query("156");
        Assert.assertNotNull(view);
        view = query.query("510504001");
        Assert.assertNotNull(view);
        System.out.println(view);
        view = query.query("5234001");
        Assert.assertNull(view);
    }

    @Test
    public void testQueryByJurisdiction() {
        AreaView[] views = query.queryByJurisdiction("156");
        Assert.assertEquals(views.length, 2);
        views = query.queryByJurisdiction("510500");
        Assert.assertEquals(views.length, 3);
    }

    @Test
    public void testQueryCountry() {
        AreaView[] views = query.queryCountry();
        Assert.assertEquals(views.length, 1);

    }
}