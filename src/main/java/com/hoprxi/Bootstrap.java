package com.hoprxi;

import com.hoprxi.rest.AreaSevice;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.encoding.EncodingService;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.server.throttling.ThrottlingService;
import com.linecorp.armeria.server.throttling.ThrottlingStrategy;
import jakarta.servlet.ServletException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-13
 */
public final class Bootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);
    private static final Pattern EXCLUDE = Pattern.compile("^-{1,}.*");

    public static void main(String[] args) throws ServletException {
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
                    break;
                case "-h":
                case "--help":
                    System.out.println("Non-option arguments:\n" +
                            "command              \n" +
                            "\n" +
                            "Option                         Description        \n" +
                            "------                         -----------        \n" +
                            "-f, --file <filename>          A file that stores the key\n" +
                            "-e <KeyValuePair>              encrypt a passwd\n" +
                            "-l, --list                     entries in the keystore\n" +
                            "-h, --help                     Show help          \n");
                    break;
            }
        }
        StoreKeyLoad.loadSecretKey(fileName, fileProtectedPasswd, entries.toArray(new String[0]));

        ServerBuilder sb = Server.builder();
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
        /* 添加文档服务
        sb.serviceUnder("/docs", DocService.builder()
                .exampleRequests(AreaSevice.class)
                .build());
         */
        Server server = sb.http(9000)
                .annotatedService("/",new AreaSevice())
                .service("/", (ctx, req) -> HttpResponse.of("Hello, Armeria!"))
                .build();
        server.closeOnJvmShutdown();
        server.start().join();
        System.out.println(String.format("Server has been started. Serving dummy service at http://127.0.0.1:%d",
                server.activeLocalPort()));

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            System.out.println("Server stopped");
        }));
    }
}
