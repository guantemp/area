package com.hoprxi.infrastructure.persistence;

import com.hoprxi.application.AreaBatchImport;
import com.hoprxi.infrastructure.DecryptUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-09
 */
public class ESAreaBatchImport implements AreaBatchImport {
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClientBuilder BUILDER;

    static {
        Config config = ConfigFactory.load("area");
        Config read = config.getConfigList("read").get(0);
        String host = "";
        int port = 9200;
        String entry = host + ":" + port;
        String user = DecryptUtil.decrypt(entry, read.getString("user"));
        String password = DecryptUtil.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)))
                .addHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8");
        //builder.setHttpAsyncResponseConsumerFactory(
        //new HttpAsyncResponseConsumerFactory
        //.HeapBufferedResponseConsumerFactory(30 * 1024 * 1024 * 1024));
        COMMON_OPTIONS = builder.build();
        BUILDER = RestClient.builder(new HttpHost(host, port, "https"));
    }


    @Override
    public void importXlsFrom(InputStream is) {
        Request request = new Request("POST", "/area");
        request.setOptions(COMMON_OPTIONS);
    }
}
