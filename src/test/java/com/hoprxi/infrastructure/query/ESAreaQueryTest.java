/*
 * Copyright (c) 2025. www.hoprxi.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.hoprxi.infrastructure.query;

import org.testng.annotations.Test;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2026-02-15
 */
public class ESAreaQueryTest {
    static {
        StoreKeyLoad.loadSecretKey("keystore.jks", "Qwe123465",
                new String[]{"slave.tooo.top:9200"});
    }

    private static final ESAreaQuery query = new ESAreaQuery();

    @Test
    public void testFind() {
        try (InputStream is = query.find(510000)) {
            System.out.println("查询四川：\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (InputStream is = query.find(156)) {
            System.out.println("查询中国：\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (InputStream is = query.query("泸州小市", EnumSet.of(ESAreaQuery.Level.COUNTRY, ESAreaQuery.Level.CITY, ESAreaQuery.Level.COUNTY, ESAreaQuery.Level.TOWN),
                0, 999)) {
            System.out.println("\"名字模糊查询（过滤）：泸州小市\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (InputStream is = query.query(EnumSet.of(ESAreaQuery.Level.PROVINCE), null, 50)) {
            System.out.println("\"全局查询：\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream is = query.find(5100090)) {
            System.out.println("查询四川：\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testFindCountry() {
        try (InputStream is = query.queryCountry()) {
            System.out.println("国家查询：：\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testFindJurisdiction() {
        try (InputStream is = query.queryChildren(510500)) {
            System.out.println("辖区查询：\n" + new String(is.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}