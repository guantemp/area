package com.hoprxi.rest;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.hoprxi.application.AreaSearchException;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.persistence.ESAreaRepository;
import com.hoprxi.infrastructure.query.ESAreaQuery;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.stream.StreamMessage;
import com.linecorp.armeria.common.stream.StreamWriter;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/13
 *          <p>
 *          restful http<br/>
 *          areas return all <br/>
 *          areas/code return area where key=area.code,such as:areas/51000
 *          areas/code/juri(jurisdiction) return jurisdiction area where area code,such as:areas/51000/juri
 *          <br/>
 *          <ul>
 *          parameter:
 *          <li>query=name、abbreviation、mnemonic and filters=country,province,city,county,town</li>
 *          <li>fields=name,pinyin,abbreviation, initials,alias, wgs84, zipcode,telephoneCode</li>
 *          </ul>
 *          </p>
 */
@PathPrefix("/v1")
public class AreaSevice {
    private static final int OFFSET = 0;
    private static final int SIZE = 64;
    private static final int BUFFER_SIZE = 1024; // 8KB缓冲区

    private final ESAreaQuery query = new ESAreaQuery();
    private final AreaRepository repository = new ESAreaRepository();
    private final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    @Get("/areas/{code}")
    @Description("Retrieves the area information by the given area code.")
    public HttpResponse find(ServiceRequestContext ctx, @Param("code") int code, @Param("pretty") @Default("false") boolean pretty) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.whenRequestCancelled().thenAccept(stream::close);
        ctx.blockingTaskExecutor().execute(() -> {
            if (ctx.isCancelled() || ctx.isTimedOut()) return;
            ByteBuf buffer = ctx.alloc().buffer(BUFFER_SIZE);
            try (OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                 InputStream is = query.query(code); JsonParser parser = JSON_FACTORY.createParser(is)) {
                if (pretty) gen.useDefaultPrettyPrinter();
                while (parser.nextToken() != null) {
                    gen.copyCurrentEvent(parser);
                }
                gen.flush();
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
                buffer = null;
                stream.close();

            } catch (AreaSearchException e) {
                stream.write(ResponseHeaders.of(HttpStatus.NOT_FOUND));
                stream.write(HttpData.ofUtf8("{\"status\":\"not_found\",\"code\":404,\"message\":\"it's %s \"}", e.getMessage()));
                stream.close();
            } catch (IOException e) {
                this.handleStreamError(stream, e);
            } finally {
                if (buffer != null) buffer.release(); // 只释放未被转移的缓冲区
            }
        });
        return HttpResponse.of(stream);
    }

    private void copyRaw(JsonGenerator generator, JsonParser parser) throws IOException {
        while (parser.nextToken() != null) {
            generator.copyCurrentEvent(parser);
        }
    }

    @Get("/areas/{code}/juri")
    public HttpResponse queryJurisdiction(ServiceRequestContext ctx, @Param("code") int code,
                                          @Param("pretty") @Default("false") boolean pretty) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.blockingTaskExecutor().execute(() -> {
            ByteBuf buffer = ctx.alloc().buffer(BUFFER_SIZE);
            try (OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os)) {
                if (pretty) gen.useDefaultPrettyPrinter();
                //this.copyRaw(gen, query.queryJurisdiction(code));
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
                buffer = null;
                stream.close();
            } catch (IOException e) {
                this.handleStreamError(stream, e);
            } finally {
                if (buffer != null) buffer.release(); // 只释放未被转移的缓冲区
            }
        });
        return HttpResponse.of(stream);
    }

    @Get("/areas")
    public HttpResponse search(ServiceRequestContext ctx, QueryParams params,
                               @Param("pretty") @Default("false") boolean pretty) {
        int offset = params.getInt("offset", OFFSET);
        int size = params.getInt("size", SIZE);
        EnumSet<ESAreaQuery.Level> sets = EnumSet.noneOf(ESAreaQuery.Level.class);
        Optional.ofNullable(params.get("filter")).filter(f -> !f.isBlank()).ifPresent(f -> {
            String[] temps = f.split(",");
            for (String temp : temps) {
                ESAreaQuery.Level level = ESAreaQuery.Level.of(temp);
                if (level != null)
                    sets.add(level);
            }
        });
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        Optional.ofNullable(params.get("q")).filter(q -> !q.isBlank()).ifPresentOrElse(q -> {//key 查询
            ctx.blockingTaskExecutor().execute(() -> {
                ByteBuf buffer = ctx.alloc().buffer(BUFFER_SIZE);
                try (OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os)) {
                    if (pretty) gen.useDefaultPrettyPrinter();
                    //this.copyRaw(gen, this.query.query(q, sets, offset, size));
                    stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                    stream.write(HttpData.wrap(buffer));
                    buffer = null;
                    stream.close();
                } catch (IOException e) {
                    this.handleStreamError(stream, e);
                } finally {
                    if (buffer != null) buffer.release(); // 只释放未被转移的缓冲区
                }
            });
        }, () -> {//全局查询
            String searchAfter = params.get("searchAfter", "");
            ctx.blockingTaskExecutor().execute(() -> {
                ByteBuf buffer = ctx.alloc().buffer(BUFFER_SIZE);
                try (OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os)) {
                    //this.copyRaw(gen, this.query.query(sets, searchAfter, size));
                    stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                    stream.write(HttpData.wrap(buffer));
                    buffer = null;
                    stream.close();
                } catch (IOException e) {
                    this.handleStreamError(stream, e);
                } finally {
                    if (buffer != null) buffer.release(); // 只释放未被转移的缓冲区
                }
            });
        });
        return HttpResponse.of(stream);
    }

    private void copyRaw(JsonGenerator generator, InputStream is) throws IOException {
        try (JsonParser parser = JSON_FACTORY.createParser(is)) {
            while (parser.nextToken() != null) {
                generator.copyCurrentEvent(parser);
            }
        }
    }

    private void handleStreamError(StreamWriter<HttpObject> stream, IOException e) {
        stream.write(ResponseHeaders.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
        stream.write(HttpData.ofUtf8("{\"status\":\"error\",\"code\":500,\"message\":\"Error,it's %s\"}", e.getMessage()));
        stream.close();
    }

    @StatusCode(201)
    @Post("/areas")
    public HttpResponse create(ServiceRequestContext ctx, HttpRequest req, HttpData body) {
        RequestHeaders headers = req.headers();
        if (!(MediaType.JSON.is(headers.contentType()) || MediaType.JSON_UTF_8.is(headers.contentType())))
            return HttpResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    MediaType.PLAIN_TEXT_UTF_8, "Expected JSON content");
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        ctx.blockingTaskExecutor().execute(() -> {
            try (JsonParser parser = JSON_FACTORY.createParser(body.toInputStream())) {
                Area area = parserJson(parser, -1);
                repository.save(area);
                ctx.eventLoop().execute(() -> future.complete(HttpResponse.of(HttpStatus.CREATED, MediaType.JSON_UTF_8,
                        "{\"status\":\"success\",\"code\":201,\"message\":\"A area created,it's %s\"}", area)));
            } catch (Exception e) {
                ctx.eventLoop().execute(() -> future.complete(HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8, "{\"status\":500,\"code\":500,\"message\":\"Can't create a area,cause by {}\"}", e)));
            }
        });
        return HttpResponse.of(future);
    }

    @StatusCode(201)
    @Put("/areas/{code}")
    public HttpResponse update(ServiceRequestContext ctx, @Param("code") @Default("-1") int code, HttpData body) {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        ctx.blockingTaskExecutor().execute(() -> {
            try (JsonParser parser = JSON_FACTORY.createParser(body.toInputStream())) {
                Area area = parserJson(parser, code);
                repository.save(area);
                ctx.eventLoop().execute(() -> future.complete(HttpResponse.of(HttpStatus.CREATED, MediaType.JSON_UTF_8,
                        "{\"status\":\"success\",\"code\":201,\"message\":\"The area(%s) has update\"}", area)));
            } catch (Exception e) {
                ctx.eventLoop().execute(() -> future.complete(HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8, "{\"status\":500,\"code\":500,\"message\":\"Update fail,cause by {}\"}", e)));
            }
        });
        return HttpResponse.of(future);
    }

    private Area parserJson(JsonParser parser, int code) throws IOException {
        int parentCode = 0, level = -1;
        String name = "", abbreviation = "";
        String zipcode = null, alias = null, telephoneCode = null;
        WGS84 wgs84 = null;
        while (parser.nextToken() != null) {
            if (JsonToken.FIELD_NAME == parser.currentToken()) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case "code" -> code = parser.getIntValue();
                    case "parent_code" -> parentCode = parser.getIntValue();
                    case "name" -> name = parser.getValueAsString();
                    case "abbreviation" -> abbreviation = parser.getValueAsString();
                    case "alias" -> alias = parser.getValueAsString();
                    case "zipcode" -> zipcode = parser.getValueAsString();
                    case "telephoneCode" -> telephoneCode = parser.getValueAsString();
                    case "location" -> wgs84 = deserialize(parser);
                    case "level" -> level = parser.getIntValue();
                }
            }
        }
        return switch (level) {
            case 0 -> new Country(code, parentCode, new Name(name, abbreviation, alias), wgs84, zipcode, telephoneCode);
            case 1 ->
                    new Province(code, parentCode, new Name(name, abbreviation, alias), wgs84, zipcode, telephoneCode);
            case 2 -> new City(code, parentCode, new Name(name, abbreviation, alias), wgs84, zipcode, telephoneCode);
            case 3 -> new County(code, parentCode, new Name(name, abbreviation, alias), wgs84, zipcode, telephoneCode);
            case 4 -> new Town(code, parentCode, new Name(name, abbreviation, alias), wgs84, zipcode, telephoneCode);
            default -> throw new IllegalStateException("Unexpected value: " + level);
        };
    }

    private WGS84 deserialize(JsonParser parser) throws IOException {
        double longitude = 0.0, latitude = 0.0;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case "lon":
                    longitude = parser.getValueAsDouble(0.0);
                    break;
                case "lat":
                    latitude = parser.getValueAsDouble(0.0);
                    break;
            }
        }
        return new WGS84(longitude, latitude);
    }
}
