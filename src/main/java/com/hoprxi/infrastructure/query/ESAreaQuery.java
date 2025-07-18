package com.hoprxi.infrastructure.query;

import com.fasterxml.jackson.core.*;
import com.hoprxi.infrastructure.DecryptUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.elasticsearch.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2025-07-11
 */
public class ESAreaQuery {
    private static final Logger LOGGER = LoggerFactory.getLogger("ESAreaQuery");
    private static final RequestOptions COMMON_OPTIONS;
    private static final RestClientBuilder BUILDER;
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    static {
        Config config = ConfigFactory.load("area");
        Config read = config.getConfigList("read").getFirst();
        String host = read.getString("host");
        int port = read.getInt("port");
        String entry = host + ":" + port;
        String user = DecryptUtil.decrypt(entry, read.getString("user"));
        String password = DecryptUtil.decrypt(entry, read.getString("password"));

        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        builder.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8))).addHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=utf-8");
        //builder.setHttpAsyncResponseConsumerFactory(
        //new HttpAsyncResponseConsumerFactory
        //.HeapBufferedResponseConsumerFactory(30 * 1024 * 1024 * 1024));
        COMMON_OPTIONS = builder.build();
        BUILDER = RestClient.builder(new HttpHost(host, port, "https"));
    }

    public OutputStream query(int code) {
        try (RestClient client = BUILDER.build()) {
            Request request = new Request("GET", "/area/_doc/" + code);
            request.setOptions(COMMON_OPTIONS);
            OutputStream os = new ByteArrayOutputStream();
            Response response = client.performRequest(request);
            JsonParser parser = JSON_FACTORY.createParser(response.getEntity().getContent());
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT && "_source".equals(parser.getCurrentName())) {
                    JsonGenerator generator = JSON_FACTORY.createGenerator(os, JsonEncoding.UTF8);
                    generator.writeStartObject();
                    while (parser.nextToken() != null) {
                        //System.out.println(parser.currentToken()+":"+parser.getCurrentName());
                        if (parser.currentToken() == JsonToken.END_OBJECT && "_source".equals(parser.getCurrentName()))
                            break;
                        generator.copyCurrentEvent(parser);
                    }
                    generator.writeEndObject();
                    generator.close();
                    break;
                }
            }
            return os;
        } catch (IOException e) {
            System.out.println(e);
            //LOGGER.error("The brand(id={}) can't retrieve", id, e);
        }
        return new ByteArrayOutputStream(0);
    }

    public OutputStream queryCountry() {
        try (RestClient client = BUILDER.build()) {
            Request request = new Request("GET", "/area/_search");
            request.setOptions(COMMON_OPTIONS);
            request.setJsonEntity(rootJsonEntity());
            OutputStream os = new ByteArrayOutputStream();
            Response response = client.performRequest(request);
            JsonParser parser = JSON_FACTORY.createParser(response.getEntity().getContent());
            return os;
        } catch (IOException e) {
            System.out.println(e);
            //LOGGER.error("The brand(id={}) can't retrieve", id, e);
        }
        return new ByteArrayOutputStream(0);
    }

    private String rootJsonEntity() {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeObjectFieldStart("query");
            generator.writeObjectFieldStart("bool");
            generator.writeObjectFieldStart("filter");
            generator.writeObjectFieldStart("script");
            generator.writeObjectFieldStart("script");
            generator.writeStringField("lang", "painless");
            generator.writeStringField("source", "doc['code'].value == doc['parent_code'].value");
        } catch (IOException e) {
            LOGGER.error("Cannot assemble request JSON", e);
        }
        return writer.toString();
    }
}
