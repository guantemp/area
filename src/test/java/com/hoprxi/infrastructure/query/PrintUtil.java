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

package com.hoprxi.infrastructure.query;


import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2026/5/11
 */

public class PrintUtil {

    public static void printMono(Mono<ByteBuf>[] monos) {
        // 1. 使用 Mono.when 合并
        // 这会让数组里所有的 Mono 同时开始执行（并发）
        // 注意：Mono.when 会等待所有 Mono 完成，但只返回 Void (不返回具体数据)
        // 如果你想处理数据，需要用 Mono.zip 或者分别 subscribe
        // 这里为了演示简单且保留数据内容，我们改用 Flux.merge 或者分别订阅

        // 方案 A：如果只是想等它们都跑完（不管数据内容，或者数据内容已经在内部处理了）
        // Mono.when(monos).subscribe(() -> System.out.println("全部完成"));

        // 方案 B (推荐)：为了能看到每个 ID 的具体返回内容，我们利用 Flux.merge 把它们当成流处理
        // 或者简单地遍历订阅（这也是并发执行的）
        System.out.println(">>> 开始并发执行 " + monos.length + " 个 Mono 任务...");
        // 使用 Flux.fromArray 将数组转为流，并用 index() 自动带上索引
        Flux.fromArray(monos)
                .index() // 此时流中的元素变成了 Tuple2<Long, Mono<ByteBuf>>
                .flatMap(tuple -> {
                    long index = tuple.getT1(); // 获取索引
                    Mono<ByteBuf> mono = tuple.getT2(); // 获取原始的 Mono

                    return mono
                            .doOnError(e -> System.err.println("任务[" + index + "] 发生错误: " + e.getMessage()))
                            .onErrorResume(e -> Mono.empty()) // 发生错误返回空，不中断整体流程
                            // 把索引和 ByteBuf 再次打包，方便后续打印
                            .map(byteBuf -> Tuples.of(index, byteBuf));
                })
                // flatMap 默认就是并发的（concurrency 默认为 256，足以覆盖绝大多数场景）
                .doOnNext(entry -> {
                    long index = entry.getT1();           // 索引
                    ByteBuf byteBuf = entry.getT2();      // 数据
                    String content = byteBuf.toString(StandardCharsets.UTF_8);
                    System.out.println("收到任务[" + index + "]的结果，长度: " + content.length());
                    System.out.println(content); // 如果需要看具体 JSON 可以放开
                })
                .then() // 忽略具体元素，只等待所有任务完成
                .block(Duration.ofSeconds(30));
    }

    public static void printFlux(Flux<ByteBuf>[] fluxes) {
        int total = fluxes.length;
        CountDownLatch latch = new CountDownLatch(total);

        for (int i = 0; i < total; i++) {
            final int idx = i;
            fluxes[i]
                    .subscribeOn(Schedulers.parallel())
                    .collectList()
                    .subscribe(
                            list -> {
                                StringBuilder sb = new StringBuilder();
                                sb.append("\n========== [Query-").append(idx).append("] 开始 ==========\n");

                                for (ByteBuf byteBuf : list) {
                                    try {
                                        // 【修改点】不使用 ByteBufUtil，改用原生方式读取
                                        // 1. 获取可读字节数
                                        int readableBytes = byteBuf.readableBytes();
                                        // 2. 创建字节数组
                                        byte[] bytes = new byte[readableBytes];
                                        // 3. 将 ByteBuf 的数据拷贝到数组 (getBytes 不会移动读写指针)
                                        byteBuf.getBytes(byteBuf.readerIndex(), bytes);
                                        // 4. 转为字符串
                                        String content = new String(bytes, StandardCharsets.UTF_8);

                                        sb.append(content).append("\n");
                                    } catch (Exception e) {
                                        sb.append("[ERROR] 解码失败: ").append(e.getMessage()).append("\n");
                                    } finally {
                                        ReferenceCountUtil.safeRelease(byteBuf);
                                    }
                                }
                                sb.append("========== [Query-").append(idx).append("] 结束 ==========\n");
                                System.out.println(sb.toString());
                                latch.countDown();
                            },
                            error -> {
                                error.printStackTrace();
                                latch.countDown();
                            }
                    );
        }

        try {
            if (!latch.await(30, TimeUnit.SECONDS)) System.err.println("超时！");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
