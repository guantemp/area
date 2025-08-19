package com.hoprxi.infrastructure.persistence;


import com.fasterxml.jackson.core.*;
import com.hoprxi.domain.model.Area;
import com.hoprxi.domain.model.AreaRepository;
import com.hoprxi.infrastructure.query.ESAreaQuery;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.crypto.application.DatabaseSpecDecrypt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/18
 */

public class ESAreaRepository implements AreaRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(ESAreaRepository.class);
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClient CLIENT;
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();
    static {
        Config config = ConfigFactory.load("area");
        Config read = config.getConfigList("read").getFirst();
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DatabaseSpecDecrypt.decrypt(entry, read.getString("user"));
        String password = DatabaseSpecDecrypt.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))).addHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8");
        COMMON_OPTIONS = builder.build();
        CLIENT = RestClient.builder(new HttpHost(host, port, "https")).build();
    }
    @Override
    public Area find(int code) {
        try (OutputStream os = new ByteArrayOutputStream(128); JsonGenerator generator = JSON_FACTORY.createGenerator(os, JsonEncoding.UTF8)) {
            Request request = new Request("GET", "/area/_doc/" + code);
            request.setOptions(COMMON_OPTIONS);
            Response response = CLIENT.performRequest(request);
            JsonParser parser = JSON_FACTORY.createParser(response.getEntity().getContent());
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT && "_source".equals(parser.getCurrentName())) {
                    generator.writeStartObject();
                    while (parser.nextToken() != null) {
                        if (parser.currentToken() == JsonToken.END_OBJECT && "_source".equals(parser.getCurrentName()))
                            break;
                        generator.copyCurrentEvent(parser);
                    }
                    generator.writeEndObject();
                    break;
                }
            }
            return null;
        } catch (IOException e) {
            //System.out.println(((ResponseException)e).getResponse().getStatusLine().getStatusCode());
            LOGGER.error("The area(code={}) can't retrieve", code, e);
        }
        return null;
    }

    @Override
    public void save(Area area) {

    }

    @Override
    public void delete(int code) {

    }
}
