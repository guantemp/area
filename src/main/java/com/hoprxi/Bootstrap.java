package com.hoprxi;

import com.hoprxi.rest.AreaSevice;
import com.hoprxi.rest.IPSeekerService;
import com.hoprxi.rest.UploadFileService;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.ClientAddressSource;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.encoding.EncodingService;
import com.linecorp.armeria.server.file.FileService;
import com.linecorp.armeria.server.file.FileServiceBuilder;
import com.linecorp.armeria.server.file.HttpFile;
import com.linecorp.armeria.server.logging.LoggingService;
import com.linecorp.armeria.server.throttling.ThrottlingService;
import com.linecorp.armeria.server.throttling.ThrottlingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.util.StoreKeyLoad;

import java.nio.file.Paths;
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
        FileServiceBuilder fsb =
                FileService.builder(Paths.get(System.getProperty("user.dir"), "/html"));
        fsb.autoIndex(true);
        FileService fs = fsb.build();
        sb.serviceUnder("/html", fs);
        HttpFile index = HttpFile.of(Paths.get(System.getProperty("user.dir"), "/html/upload.html"));
        sb.serviceUnder("/", index.asService());//相当于缺省index.html

        //添加文档服务
        sb.serviceUnder("/docs", DocService.builder()
                .exampleRequests("/v1/areas", "query")
                .build());
        sb.http(PORT);
        //sb.https(PORT+1).tls(new File("certificate.crt"), new File("private.key"), "myPassphrase");

        Server server = sb.annotatedService("/", new AreaSevice())
                .annotatedService("/", new IPSeekerService())
                .annotatedService("/", new UploadFileService())
                .build();
        server.closeOnJvmShutdown();
        server.start().join();
        System.out.printf("Server has been started. Serving dummy service at http://127.0.0.1:%d%n and at https://127.0.0.1:%d%n",
                server.activeLocalPort(SessionProtocol.HTTP), server.activeLocalPort(SessionProtocol.HTTP));

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            System.out.println("Server stopped");
        }));
    }
}
