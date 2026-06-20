package com.hoprxi.rest;


import com.fasterxml.jackson.core.*;
import com.hoprxi.application.NotFoundException;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.persistence.ESAreaRepository;
import com.hoprxi.infrastructure.query.ESAreaQuery;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.*;
import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;


/**
 * 区域信息服务类，提供对地理区域数据（国家、省、市、县、乡镇等）的查询、创建与更新能力。
 * <p>
 * 所有读取操作均通过 Elasticsearch 后端实现，并以流式方式返回 JSON 响应，避免大对象全量加载到内存。
 * 写入操作（创建/更新）在阻塞任务线程池中执行，确保不阻塞 Armeria 的事件循环线程。
 *
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @version 0.0.2 builder 2026/3/29
 * @since JDK21
 */
@PathPrefix("/v1")
public class AreaService {
    private static final int OFFSET = 0;
    private static final int SIZE = 64;
    private static final Logger LOGGER = LoggerFactory.getLogger(AreaService.class);
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();
    private static final ExecutorService VIRTUAL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ESAreaQuery query = new ESAreaQuery();
    private final AreaRepository repository = new ESAreaRepository();


    /**
     * 根据区域编码查询单个区域信息。
     *
     * @param code 区域唯一编码（如：100000 表示中国）
     * @return {@link HttpResponse} 流式响应：
     * <ul>
     *   <li>成功：HTTP 200 + JSON 格式的区域对象</li>
     *   <li>未找到：HTTP 404 + 错误 JSON</li>
     *   <li>IO 异常：HTTP 500 + 错误 JSON</li>
     * </ul>
     * @see ESAreaQuery#find(int)
     */
    @Get("/areas/{code}")
    @Description("Retrieves the area information by the given area code.")
    public Mono<HttpResponse> find(@Param("code") int code) {
        return query.findAsync(code)
                .map(byteBuf -> HttpResponse.of(
                        ResponseHeaders.builder(HttpStatus.OK)
                                .contentType(MediaType.JSON_UTF_8)
                                .build(),
                        HttpData.wrap(byteBuf)
                ))
                .onErrorResume(NotFoundException.class, error ->
                        Mono.just(HttpResponse.of(
                                ResponseHeaders.builder(HttpStatus.NOT_FOUND)
                                        .contentType(MediaType.JSON_UTF_8)
                                        .build(),
                                HttpData.ofUtf8(String.format("{\"Error\":\"Area not found: %d\"}", code))
                        ))
                )
                .onErrorResume(error ->
                        Mono.just(HttpResponse.of(
                                ResponseHeaders.builder(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .contentType(MediaType.JSON_UTF_8)
                                        .build(),
                                HttpData.ofUtf8("{\"Error\":\"Internal server error\"}")
                        ))
                );
    }

    /**
     * 查询指定区域的所有直接子区域（如查询北京市的下辖区县）。
     *
     * @param code 父区域编码
     * @return {@link HttpResponse} 流式响应：
     * <ul>
     *   <li>成功：HTTP 200 + JSON 数组（子区域列表）</li>
     *   <li>父区域不存在：HTTP 404</li>
     *   <li>IO 或解析失败：HTTP 500</li>
     * </ul>
     * @see ESAreaQuery#children(int)
     */
    @Get("/areas/{code}/children")
    public HttpResponse queryChildren(@Param("code") int code) {
        Flux<ByteBuf> dataFlux = query.childrenAsync(code);
        // 使用 switchMap：一旦有第一个元素，就前置 headers
        Flux<HttpObject> responseStream = dataFlux
                .map(HttpData::wrap)
                .transform(flux -> {
                    AtomicBoolean headersSent = new AtomicBoolean(false);
                    return flux.concatMap(data -> {
                        if (!headersSent.getAndSet(true)) {
                            // 第一个数据：先响应头 200，再发数据
                            return Flux.concat(
                                    Flux.just(ResponseHeaders.builder(HttpStatus.OK)
                                            .contentType(MediaType.JSON_UTF_8)
                                            .build()),
                                    Flux.just(data)
                            );
                        }
                        return Flux.just(data);
                    });
                })
                .switchIfEmpty(Flux.just(
                        ResponseHeaders.of(HttpStatus.NOT_FOUND),
                        HttpData.ofUtf8("{\"Warn\":\"No find children.\"}")
                ))
                .onErrorResume(new Function<Throwable, Publisher<? extends HttpObject>>() {
                    @Override
                    public Publisher<? extends HttpObject> apply(Throwable throwable) {
                        if (throwable instanceof IllegalArgumentException) {
                            return Flux.just(
                                    ResponseHeaders.of(HttpStatus.BAD_REQUEST),
                                    HttpData.ofUtf8(String.format("{\"error\":\"Invalid parameter: %s\"}", throwable.getMessage()))
                            );
                        }
                        LOGGER.error("Data stream error", throwable);
                        return Flux.just(
                                ResponseHeaders.of(HttpStatus.INTERNAL_SERVER_ERROR),
                                HttpData.ofUtf8("{\"error\":\"Internal server error\"}")
                        );
                    }
                });
        return HttpResponse.of(responseStream);
    }

    /**
     * 按条件搜索区域数据，支持关键字全文检索或全局枚举查询。
     *
     * <h3>请求参数</h3>
     * <ul>
     *   <li>{@code q}（可选）：搜索关键字。若提供，则执行全文检索；否则返回全球顶级区域（如国家列表）。</li>
     *   <li>{@code filter}（可选）：按行政级别过滤，多个值用逗号分隔（如：{@code "country,province"}）。
     *       支持级别：{@code country(0), province(1), city(2), county(3), town(4)}。</li>
     *   <li>{@code offset}（可选，默认 0）：分页偏移量。</li>
     *   <li>{@code size}（可选，默认 64）：每页大小，最大建议不超过 100。</li>
     *   <li>{@code searchAfter}（可选）：用于深度分页的游标（当前未使用，保留扩展）。</li>
     * </ul>
     *
     * @param ctx    当前服务请求上下文
     * @param params HTTP 查询参数集合
     * @return {@link HttpResponse} 流式响应：
     * <ul>
     *   <li>成功：HTTP 200 + JSON 搜索结果（含 hits 和 metadata）</li>
     *   <li>查询无效或无结果：HTTP 404</li>
     *   <li>系统错误：HTTP 500</li>
     * </ul>
     */
    @Get("/areas")
    public HttpResponse search(ServiceRequestContext ctx, QueryParams params) {
        int offset = params.getInt("offset", OFFSET);
        int size = params.getInt("size", SIZE);
        EnumSet<ESAreaQuery.Level> filters = EnumSet.noneOf(ESAreaQuery.Level.class);
        Optional.ofNullable(params.get("filter")).filter(f -> !f.isBlank()).ifPresent(f -> {
            String[] temps = f.split(",");
            for (String temp : temps) {
                ESAreaQuery.Level level = ESAreaQuery.Level.of(temp);
                if (level != null)
                    filters.add(level);
            }
        });
        Flux<ByteBuf> dataFlux = Optional.ofNullable(params.get("q"))
                .map(q -> {
                    String searchAfter = params.get("searchAfter", "");
                    return searchAfter.isBlank() ? query.queryAsync(q, filters, offset, size) :
                            query.queryAsync(filters, searchAfter, size);
                })
                .orElseGet(query::countryAsync);
        // 使用 switchMap：一旦有第一个元素，就前置 headers
        Flux<HttpObject> responseStream = dataFlux
                .map(HttpData::wrap)
                .transform(flux -> {
                    AtomicBoolean headersSent = new AtomicBoolean(false);
                    return flux.concatMap(data -> {
                        if (!headersSent.getAndSet(true)) {
                            // 第一个数据：先响应头 200，再发数据
                            return Flux.concat(
                                    Flux.just(ResponseHeaders.builder(HttpStatus.OK)
                                            .contentType(MediaType.JSON_UTF_8)
                                            .build()),
                                    Flux.just(data)
                            );
                        }
                        return Flux.just(data);
                    });
                })
                .switchIfEmpty(Flux.just(
                        ResponseHeaders.of(HttpStatus.NOT_FOUND),
                        HttpData.ofUtf8("{\"Warn\":\"No area found for search keyword.\"}")
                ))
                .onErrorResume(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        return Flux.just(
                                ResponseHeaders.of(HttpStatus.BAD_REQUEST),
                                HttpData.ofUtf8(String.format("{\"error\":\"Invalid parameter: %s\"}", throwable.getMessage()))
                        );
                    }
                    LOGGER.error("Data stream error", throwable);
                    return Flux.just(
                            ResponseHeaders.of(HttpStatus.INTERNAL_SERVER_ERROR),
                            HttpData.ofUtf8("{\"error\":\"Internal server error\"}")
                    );
                });
        return HttpResponse.of(responseStream);
/*
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        Optional.ofNullable(params.get("q")).filter(q -> !q.isBlank()).ifPresentOrElse(q -> {//key 查询
            ctx.blockingTaskExecutor().execute(() -> {
                if (ctx.isCancelled() || ctx.isTimedOut()) return;

                ByteBuf buffer = ctx.alloc().buffer(BATCH_BUFFER_SIZE);
                try (InputStream is = query.query(q, sets, offset, size); JsonParser parser = JSON_FACTORY.createParser(is);
                     OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os);) {
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
        }, () -> {//全局查询,无关键字
            ctx.blockingTaskExecutor().execute(() -> {
                if (ctx.isCancelled() || ctx.isTimedOut()) return;
                ByteBuf buffer = ctx.alloc().buffer(BATCH_BUFFER_SIZE);
                try (InputStream is = query.country(); JsonParser parser = JSON_FACTORY.createParser(is);
                     OutputStream os = new ByteBufOutputStream(buffer); JsonGenerator gen = JSON_FACTORY.createGenerator(os);) {
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
        });
        return HttpResponse.of(stream);

 */
    }

    /**
     * 创建一个新的区域记录。
     *
     * <h3>请求要求</h3>
     * <ul>
     *   <li>Content-Type 必须为 {@code application/json}</li>
     *   <li>请求体必须包含有效的区域 JSON 对象，字段包括：
     *       {@code code, parent_code, name, level} 等（详见 {@link #parserJson(JsonParser, int)}）</li>
     * </ul>
     *
     * @param ctx  当前服务请求上下文
     * @param req  完整的 HTTP 请求（用于校验 Content-Type）
     * @param body 请求体的原始字节数据（JSON）
     * @return {@link HttpResponse}：
     * <ul>
     *   <li>成功：HTTP 201 Created + 成功消息 JSON</li>
     *   <li>无效媒体类型：HTTP 415</li>
     *   <li>JSON 解析或保存失败：HTTP 500</li>
     * </ul>
     */
    @Post("/areas")
    public HttpResponse create(ServiceRequestContext ctx, HttpRequest req, HttpData body) {
        RequestHeaders headers = ctx.request().headers();
        MediaType contentType = headers.contentType();
        if (contentType == null || !(MediaType.JSON.is(contentType) || MediaType.JSON_UTF_8.is(contentType)))
            return HttpResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MediaType.JSON_UTF_8,
                    "{\"status\":415,\"code\":415,\"message\":\"Expected JSON content\"}");
        CompletableFuture<HttpResponse> future = CompletableFuture.supplyAsync(() -> {
            if (body.isEmpty())
                return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.JSON_UTF_8,
                        "{\"status\":400,\"code\":400,\"message\":\"Empty request body\"}");
            try (JsonParser parser = JSON_FACTORY.createParser(body.toInputStream());
                 ByteArrayOutputStream baos = new ByteArrayOutputStream(); JsonGenerator gen = JSON_FACTORY.createGenerator(baos, JsonEncoding.UTF8)) {
                Area area = AreaService.parserJson(parser, -1);
                // 可选：校验 area 与 code 是否一致
                repository.save(area);
                AreaService.writeAreaResponse(gen, area);
                LOGGER.info("A area has created: {}", area);
                return HttpResponse.of(HttpStatus.CREATED, MediaType.JSON_UTF_8, HttpData.wrap(baos.toByteArray()));
            } catch (JsonProcessingException e) {
                // 5. 统一异常处理，避免泄露敏感信息
                return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.JSON_UTF_8,
                        "{\"status\":400,\"message\":\"Invalid JSON format\"}");
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8,
                        "{\"status\":500,\"message\":\"Internal server error\"}");
            }
        }, VIRTUAL_EXECUTOR);
        return HttpResponse.of(future.exceptionally(throwable -> {
            LOGGER.error("Unexpected error in async task", throwable);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8,
                    "{\"status\":500,\"message\":\"Internal server error\"}");
        }));
    }

    private static void writeAreaResponse(JsonGenerator generator, Area area) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("status", "success");
        generator.writeStringField("message", "A area created, it's " + area.name().name());

        generator.writeObjectFieldStart("area");
        generator.writeNumberField("code", area.code());
        generator.writeNumberField("parent_code", area.parentCode());

        generator.writeObjectFieldStart("name");
        generator.writeStringField("name", area.name().name());
        generator.writeStringField("abbreviation", area.name().abbreviation());
        generator.writeStringField("alias", area.name().alias());
        generator.writeEndObject();//end name

        generator.writeObjectFieldStart("location");
        generator.writeNumberField("latitude", area.location().latitude());
        generator.writeNumberField("longitude", area.location().longitude());
        generator.writeEndObject();//end level

        generator.writeObjectFieldStart("level");
        generator.writeStringField("name", area.level().name());
        generator.writeNumberField("ordinal", area.level().ordinal());
        generator.writeEndObject();//end level

        generator.writeStringField("telephoneCode", area.telephoneCode());
        generator.writeStringField("zipcode", area.zipcode());
        generator.writeEndObject();//end area

        generator.writeEndObject();
        generator.close();
    }

    /**
     * 更新现有区域信息（通过区域编码匹配）。
     *
     * <h3>说明</h3>
     * 若区域不存在，Elasticsearch 会自动创建新文档（upsert 行为）。
     *
     * @param ctx  当前服务请求上下文
     * @param code 要更新的区域编码（路径参数）
     * @param body 更新的 JSON 数据（必须包含完整或部分字段）
     * @return {@link HttpResponse}：
     * <ul>
     *   <li>成功：HTTP 201 + 更新成功消息</li>
     *   <li>解析或存储失败：HTTP 500</li>
     * </ul>
     */
    @Put("/areas/{code}")
    public HttpResponse update(ServiceRequestContext ctx, @Param("code") @Default("-1") int code, HttpData body) {
        RequestHeaders headers = ctx.request().headers();
        MediaType contentType = headers.contentType();
        if (contentType == null || !(MediaType.JSON.is(contentType) || MediaType.JSON_UTF_8.is(contentType)))
            return HttpResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MediaType.JSON_UTF_8,
                    "{\"status\":415,\"code\":415,\"message\":\"Expected JSON content\"}");
        CompletableFuture<HttpResponse> future = CompletableFuture.supplyAsync(() -> {
            if (body.isEmpty())
                return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.JSON_UTF_8,
                        "{\"status\":400,\"code\":400,\"message\":\"Empty request body\"}");
            try (JsonParser parser = JSON_FACTORY.createParser(body.toInputStream())) {
                Area area = AreaService.parserJson(parser, code);
                repository.save(area);
                LOGGER.info("A area has update: {}", area);
                return HttpResponse.of(HttpStatus.CREATED, MediaType.JSON_UTF_8,
                        "{\"status\":\"success\",\"message\":\"A area has update!\"}");
            } catch (JsonProcessingException e) {
                // 5. 统一异常处理，避免泄露敏感信息
                return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.JSON_UTF_8,
                        "{\"status\":400,\"message\":\"Invalid JSON format\"}");
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8,
                        "{\"status\":500,\"message\":\"Internal server error\"}");
            }
        }, VIRTUAL_EXECUTOR);
        return HttpResponse.of(future.exceptionally(throwable -> {
            LOGGER.error("Unexpected error in async task", throwable);
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, MediaType.JSON_UTF_8,
                    "{\"status\":500,\"message\":\"Internal server error\"}");
        }));
    }

    /**
     * 从 JSON 流中解析出 {@link Area} 对象。
     *
     * @param parser JSON 解析器，当前位置应在对象起始处
     * @param code   若为更新操作，此参数为路径中的区域编码；若为创建，应为 -1（由 JSON 中的 code 字段覆盖）
     * @return 构建完成的 {@link Area} 子类实例（Country/Province/City 等）
     * @throws IOException           JSON 解析失败
     * @throws IllegalStateException 区域级别（level）非法
     */
    private static Area parserJson(JsonParser parser, int code) throws IOException {
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

    private static WGS84 deserialize(JsonParser parser) throws IOException {
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
