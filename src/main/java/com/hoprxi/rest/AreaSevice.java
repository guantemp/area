package com.hoprxi.rest;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.persistence.PsqlAreaRepository;
import com.hoprxi.infrastructure.query.ESAreaQuery;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.stream.ByteStreamMessage;
import com.linecorp.armeria.common.stream.StreamMessage;
import com.linecorp.armeria.common.stream.StreamWriter;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;

import java.io.*;
import java.util.EnumSet;
import java.util.Optional;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/13
 */
@PathPrefix("/v1")
public class AreaSevice {
    private static final int OFFSET = 0;
    private static final int SIZE = 64;
    private static final int BUFFER_SIZE = 4096; // 4KB缓冲区
    private static final PooledByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    private final ESAreaQuery query = new ESAreaQuery();
    private final AreaRepository repository = new PsqlAreaRepository();
    private final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    @Get("/areas/{code}")
    public HttpResponse query(ServiceRequestContext ctx, @Param("code") int code) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.whenRequestCancelled().thenAccept(stream::close);
        ctx.blockingTaskExecutor().execute(() -> {
            if (ctx.isCancelled()) return;
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                OutputStream source = query.query(code);
                copyRaw(gen, source);
                gen.close();
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
            } catch (IOException e) {
                handleStreamError(stream, e);
            } finally {
                stream.close();
            }
        });
        return HttpResponse.of(stream);
    }

    @Get("/areas/{code}/juri")
    public HttpResponse queryJurisdiction(ServiceRequestContext ctx, @Param("code") int code) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.blockingTaskExecutor().execute(() -> {
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                copyRaw(gen, query.queryJurisdiction(code));
                gen.close();
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                stream.close();
            }
        });
        return HttpResponse.of(stream);
    }

    @Get("/areas")
    public HttpResponse query(ServiceRequestContext ctx) {
        QueryParams params = ctx.queryParams();
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
                try {
                    ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                    OutputStream os = new ByteBufOutputStream(buffer);
                    JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                    copyRaw(gen, this.query.query(q, sets, offset, size));
                    gen.close();
                    stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                    stream.write(HttpData.wrap(buffer));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    stream.close();
                }
            });
        }, () -> {//全局查询
            String searchAfter = params.get("searchAfter", "");
            ctx.blockingTaskExecutor().execute(() -> {
                try {
                    ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                    OutputStream os = new ByteBufOutputStream(buffer);
                    JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                    copyRaw(gen, this.query.query(sets, searchAfter, size));
                    gen.close();
                    stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                    stream.write(HttpData.wrap(buffer));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    stream.close();
                }
            });
        });
        return HttpResponse.of(stream);
    }

    private void copyRaw(JsonGenerator generator, OutputStream source) throws IOException {
        InputStream is = new ByteArrayInputStream(((ByteArrayOutputStream) source).toByteArray());
        JsonParser parser = JSON_FACTORY.createParser(is);
        while (parser.nextToken() != null) {
            generator.copyCurrentEvent(parser);
        }
    }

    private void handleStreamError(StreamWriter<HttpObject> stream, IOException e) {
        ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
        OutputStream os = new ByteBufOutputStream(buffer);
        try (JsonGenerator gen = JSON_FACTORY.createGenerator(os);) {
            gen.writeStartObject();
            gen.writeStringField("error", "Stream generation failed");
            gen.writeStringField("message", e.getMessage());
            gen.writeEndObject();
            gen.close();
            stream.write(HttpData.wrap(buffer));
        } catch (IOException ex) {
            stream.close();
            throw new RuntimeException(ex);
        }
    }

    @Post("/areas")
    public HttpResponse create(ServiceRequestContext ctx, ByteStreamMessage body) {
        RequestHeaders headers = ctx.request().headers();
        if (!"application/json".equalsIgnoreCase(headers.contentType().type())) {
            return HttpResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    MediaType.PLAIN_TEXT_UTF_8, "Expected JSON content");
        }
        ctx.blockingTaskExecutor().execute(() -> {
            try (JsonParser parser = JSON_FACTORY.createParser(body.toInputStream())) {
                Area area = parserJson(parser);
                repository.save(area);
            } catch (Exception e) {
                //return HttpResponse.of(HttpStatus.BAD_REQUEST,
                // MediaType.PLAIN_TEXT_UTF_8,
                //"JSON parse error:{} " ,e.getMessage());
            }
        });
        return HttpResponse.of(HttpStatus.OK,
                MediaType.PLAIN_TEXT_UTF_8, "Sues");
    }

    private Area parserJson(JsonParser parser) throws IOException {
        int code = 0, parentCode = 0;
        String name = "";
        String abbreviation = "";
        String zipcode = null, alias = null, telephoneCode = null;
        WGS84 wgs84 = null;
        int level = -1;
        while (parser.nextToken() != null) {
            if (JsonToken.FIELD_NAME.equals(parser.currentToken())) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                switch (fieldName) {
                    case "code" -> code = parser.getIntValue();
                    case "parentCode" -> parentCode = parser.getIntValue();
                    case "name" -> name = parser.getValueAsString();
                    case "abbreviation" -> abbreviation = parser.getValueAsString();
                    case "alias" -> alias = parser.getValueAsString();
                    case "zipcode" -> zipcode = parser.getValueAsString();
                    case "telephoneCode" -> telephoneCode = parser.getValueAsString();
                    case "bound" -> wgs84 = deserialize(parser);
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
            default -> null;
        };
    }

    private WGS84 deserialize(JsonParser parser) throws IOException {
        double longitude = 0.0, latitude = 0.0;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            parser.nextToken();
            switch (fieldName) {
                case "longitude":
                    longitude = parser.getValueAsDouble(0.0);
                    break;
                case "latitude":
                    latitude = parser.getValueAsDouble(0.0);
                    break;
            }
        }
        return new WGS84(longitude, latitude);
    }

    @Put("/areas/{code}")
    public HttpResponse update(ServiceRequestContext ctx, @Param("code") int code, ByteStreamMessage body) {
        ctx.blockingTaskExecutor().execute(() -> {
            try (JsonParser parser = JSON_FACTORY.createParser(body.toInputStream())) {
                Area area = parserJson(parser);
                repository.save(area);
            } catch (Exception e) {
                //return HttpResponse.of(HttpStatus.BAD_REQUEST,
                // MediaType.PLAIN_TEXT_UTF_8,
                //"JSON parse error:{} " ,e.getMessage());
            }
        });
        return null;
    }

    private Area parserJson(JsonParser parser, int code) throws IOException {

        return null;
    }
}
