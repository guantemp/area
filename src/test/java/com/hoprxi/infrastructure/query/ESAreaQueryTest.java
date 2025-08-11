package com.hoprxi.infrastructure.query;

import org.testng.annotations.Test;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-13
 */
public class ESAreaQueryTest {
    static {
        StoreKeyLoad.loadSecretKey("keystore.jks", "Qwe123465",
                new String[]{"slave.tooo.top:9200", "slave.tooo.top:6543:P$Qwe123465Pg", "slave.tooo.top:6379:P$Qwe123465Re"});
    }

    private static final ESAreaQuery query = new ESAreaQuery();

    @Test
    public void testQuery() {
        try (OutputStream os = query.query(510000)) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("查询四川：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (OutputStream os = query.query(156)) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("查询中国：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (OutputStream os = query.query("四", EnumSet.of(ESAreaQuery.Level.PROVINCE, ESAreaQuery.Level.CITY), 0, 999)) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("名字模糊查询（过滤）：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (OutputStream os = query.query("四", EnumSet.noneOf(ESAreaQuery.Level.class), 0, 999)) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("名字模糊查询：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (OutputStream os = query.query("", EnumSet.noneOf(ESAreaQuery.Level.class), 0, 999)) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("全局查询：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testQueryCountry() {
        try (OutputStream os = query.queryCountry()) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("国家查询：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testQueryJurisdiction() {
        try (OutputStream os = query.queryJurisdiction(510500)) {
            if (os instanceof ByteArrayOutputStream) {
                String content = ((ByteArrayOutputStream) os).toString(StandardCharsets.UTF_8);
                System.out.println("辖区查询：\n" + content);
            } else {
                System.err.println("错误：输出流不是ByteArrayOutputStream类型");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}