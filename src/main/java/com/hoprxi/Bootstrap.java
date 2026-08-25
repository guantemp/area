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
package com.hoprxi;

import com.hoprxi.rest.AreaService;
import com.hoprxi.rest.IPSeekerService;
import com.hoprxi.rest.UploadFileService;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.ClientAddressSource;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.cors.CorsService;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.encoding.EncodingService;
import com.linecorp.armeria.server.file.FileService;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.server.throttling.ThrottlingService;
import com.linecorp.armeria.server.throttling.ThrottlingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.2 builder 2026-02-25
 */
public final class Bootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);
    private static final Pattern EXCLUDE = Pattern.compile("^-{1,}.*");
    private static final int PORT = 9000;

    public static void main(String[] args) {
        String fileName = "keystore.jks", fileProtectedPasswd = "";
        Set<String> entries = new HashSet<>();
        for (int i = 0, j = args.length; i < j; i++) {
            switch (args[i]) {
                case "-f":
                case "--file":
                    if (j > i + 1) {
                        if (EXCLUDE.matcher(args[i + 1]).matches())
                            break;
                        else
                            fileName = args[i + 1];
                    }
                    if (j > i + 2) {
                        if (EXCLUDE.matcher(args[i + 2]).matches())
                            break;
                        else
                            fileProtectedPasswd = args[i + 2];
                    }
                    break;
                case "-e":
                case "--entries":
                    int k = i + 1;
                    while (k < j) {
                        if (EXCLUDE.matcher(args[k]).matches())//下一参数开始
                            break;
                        else
                            entries.add(args[k]);
                        k++;
                    }
                case "-p":
                case "--port":
                    break;
                case "-h":
                case "--help":
                    System.out.println("""
                            Non-option arguments:
                            command             \s
                            
                            Option                         Description       \s
                            -------------------            --------------------------\s
                            -f, --file <filename>          A file that stores the key
                            -e <KeyValuePair>              encrypt a passwd
                            -l, --list                     entries in the keystore
                            -h, --help                     Show help         \s
                            """);
                    break;
            }
        }
        StoreKeyLoad.loadSecretKey(fileName, fileProtectedPasswd, entries.toArray(new String[0]));

        ServerBuilder sb = Server.builder();
        // Configure a filter which evaluates whether an address of a remote endpoint is
        // trusted. If unspecified, no remote endpoint is trusted.
        // e.g. servers who have an IP address in 10.0.0.0/8.
        //sb.clientAddressTrustedProxyFilter(InetAddressPredicates.ofCidr("10.0.0.0/8"));

        // Configure a filter which evaluates whether an address can be used as
        // a client address. If unspecified, any address would be accepted.
        // e.g. public addresses

        sb.clientAddressFilter(address -> !address.isSiteLocalAddress());

        // Configure a list of sources which are used to determine where to look for
        // the client address, in the order of preference. If unspecified, 'Forwarded',
        // 'X-Forwarded-For' and the source address of a PROXY protocol header would be used.

        sb.clientAddressSources(ClientAddressSource.ofHeader(HttpHeaderNames.FORWARDED),
                ClientAddressSource.ofHeader(HttpHeaderNames.X_FORWARDED_FOR),
                ClientAddressSource.ofProxyProtocol());
        //http2 配置
        sb.http2MaxFrameSize(16384) // 16KB帧大小
                .http2InitialConnectionWindowSize(1024 * 1024) // 1MB连接窗口
                .http2InitialStreamWindowSize(512 * 1024);// 512KB流窗口
        //  添加装饰器（中间件）
        sb.decorator(LoggingService.newDecorator()); // 日志记录
        sb.decorator(EncodingService.newDecorator()); // 压缩
        sb.decorator(ThrottlingService.newDecorator( // 速率限制
                ThrottlingStrategy.rateLimiting(100) // 100请求/秒
        ));

        Path htmlDir = Paths.get(System.getProperty("user.dir"), "html");
        FileService fs = FileService.builder(htmlDir)
                .autoIndex(true)      // 开启目录浏览（可选）
                .build();
        sb.serviceUnder("/", fs);

        //添加文档服务
        sb.serviceUnder("/docs", DocService.builder()
                .exampleRequests("/v1", "query")
                .build());
        sb.http(PORT);


        sb.decorator(CorsService.builder(
                "https://www.hoperxi.com",
                        "https://slave.tooo.top"
                ) // 允许来源
                .allowRequestMethods(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.OPTIONS)
                .allowRequestHeaders(HttpHeaderNames.CONTENT_TYPE, HttpHeaderNames.AUTHORIZATION) // 如果有自定义 Header 也加在这里
                .exposeHeaders(HttpHeaderNames.AUTHORIZATION)
                .maxAge(3600) // 预检请求缓存时间（秒）
                .newDecorator());

        //sb.https(PORT+1).tls(new File("certificate.crt"), new File("private.key"), "myPassphrase");

        Server server = sb.annotatedService("/", new AreaService())
                .annotatedService("/", new IPSeekerService())
                .annotatedService("/", new UploadFileService())
                .build();
        server.closeOnJvmShutdown();
        server.start().join();
        LOGGER.info("Server has been started. Serving dummy service at http://127.0.0.1:{} and https://127.0.0.1:{}",
                server.activeLocalPort(SessionProtocol.HTTP), server.activeLocalPort(SessionProtocol.HTTP));
        System.out.printf("Server has been started. Serving dummy service at http://127.0.0.1:%d and https://127.0.0.1:%d%n",
                server.activeLocalPort(SessionProtocol.HTTP), server.activeLocalPort(SessionProtocol.HTTP));

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            System.out.println("Server stopped");
        }));
    }
}
