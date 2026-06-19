/*
 * Copyright (c) 2026. www.hoprxi.com All Rights Reserved.
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
package com.hoprxi.application;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.EnumSet;

/***
 * 地域（区域）查询服务接口。
 * <p>
 * 提供基于地域编码或关键字的检索能力，支持同步（返回 InputStream）和异步响应式（返回 Reactor 的 Mono/Flux）两种调用方式。
 * 异步方法返回的 ByteBuf 包含经过转换的 JSON 数据流，调用方需自行管理引用计数（Reference Count）。
 * </p>
 *
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.2 builder 2026-06-19
 */

public interface AreaQuery {
    /**
     * 地域层级枚举。
     * <p>
     * 定义了从国家到乡镇的五级行政区划层级。
     * </p>
     */
    enum Level {
        COUNTRY, PROVINCE, CITY, COUNTY, TOWN;

        /**
         * @param s of level name
         * @return <code>NULL if no match</code>
         */
        public static Level of(String s) {
            for (Level Level : values()) {
                if (Level.name().equalsIgnoreCase(s))
                    return Level;
            }
            return null;
        }
    }

    /**
     * 根据地域编码同步查询地域详情。
     *
     * @param code 地域行政区划编码
     * @return 包含地域详情 JSON 数据的输入流
     */
    InputStream find(int code);

    /**
     * 根据地域编码异步查询地域详情。
     *
     * @param code 地域行政区划编码
     * @return 包含地域详情 JSON 数据的异步信号（单个 ByteBuf）
     */
    Mono<ByteBuf> findAsync(int code);

    /**
     * 同步查询所有国家列表。
     *
     * @return 包含国家列表 JSON 数据的输入流
     */
    InputStream queryCountry();

    /**
     * 异步查询所有国家列表。
     *
     * @return 包含国家列表 JSON 数据的异步响应式流
     */
    Flux<ByteBuf> queryCountryAsync();

    /**
     * 根据关键字和层级过滤条件同步搜索地域。
     * <p>
     * 支持分页查询，通过 from 和 size 参数控制返回结果的范围。
     * </p>
     *
     * @param keyword 搜索关键字
     * @param filters 地域层级过滤集合，仅返回匹配指定层级的结果；传 {@code null} 或空集合表示不过滤
     * @param from    分页偏移量（从 0 开始）
     * @param size    每页返回的记录数
     * @return 包含搜索结果 JSON 数据的输入流
     */
    InputStream query(String keyword, EnumSet<AreaQuery.Level> filters, int from, int size);

    /**
     * 根据关键字和层级过滤条件异步搜索地域。
     * <p>
     * 支持分页查询，通过 from 和 size 参数控制返回结果的范围。
     * </p>
     *
     * @param keyword 搜索关键字
     * @param filters 地域层级过滤集合，仅返回匹配指定层级的结果；传 {@code null} 或空集合表示不过滤
     * @param from    分页偏移量（从 0 开始）
     * @param size    每页返回的记录数
     * @return 包含搜索结果 JSON 数据的异步响应式流
     */
    Flux<ByteBuf> queryAsync(String keyword, EnumSet<AreaQuery.Level> filters, int from, int size);

    /**
     * 使用游标（search_after）同步查询地域列表。
     * <p>
     * 适用于大数据量的深度分页场景，通过 search_after 游标实现高效的滚动查询。
     * </p>
     *
     * @param filters     地域层级过滤集合；传 {@code null} 或空集合表示不过滤
     * @param searchAfter 游标值，首次查询传 {@code null}，后续使用上一次返回结果中的游标
     * @param size        每页返回的记录数
     * @return 包含搜索结果 JSON 数据的输入流
     */
    InputStream query(EnumSet<AreaQuery.Level> filters, String searchAfter, int size);

    /**
     * 使用游标（search_after）异步查询地域列表。
     * <p>
     * 适用于大数据量的深度分页场景，通过 search_after 游标实现高效的滚动查询。
     * </p>
     *
     * @param filters     地域层级过滤集合；传 {@code null} 或空集合表示不过滤
     * @param searchAfter 游标值，首次查询传 {@code null}，后续使用上一次返回结果中的游标
     * @param size        每页返回的记录数
     * @return 包含搜索结果 JSON 数据的异步响应式流
     */
    Flux<ByteBuf> queryAsync(EnumSet<AreaQuery.Level> filters, String searchAfter, int size);

    /**
     * 同步查询指定地域的直接子级列表。
     *
     * @param code 父级地域行政区划编码
     * @return 包含子级地域列表 JSON 数据的输入流
     */
    InputStream children(int code);

    /**
     * 异步查询指定地域的直接子级列表。
     *
     * @param code 父级地域行政区划编码
     * @return 包含子级地域列表 JSON 数据的异步响应式流
     */
    Flux<ByteBuf> childrenAsync(int code);
}
