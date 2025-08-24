package com.hoprxi.rest;


import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.hoprxi.rest.ip.IPSeeker;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.stream.StreamMessage;
import com.linecorp.armeria.common.stream.StreamWriter;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Get;
import com.linecorp.armeria.server.annotation.Param;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;

import java.io.IOException;
import java.io.OutputStream;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/21
 */

public class IPSeekerService {
    private static final int BUFFER_SIZE = 1024; // 4KB缓冲区
    private static final PooledByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;
    private final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    @Get("/ipSeek/{ip}")
    public HttpResponse query(ServiceRequestContext ctx, @Param("ip") String ip) {
        StreamWriter<HttpObject> stream = StreamMessage.streaming();
        ctx.whenRequestCancelled().thenAccept(stream::close);
        ctx.blockingTaskExecutor().execute(() -> {
            if (ctx.isCancelled()) return;
            try {
                ByteBuf buffer = ALLOCATOR.buffer(BUFFER_SIZE);
                OutputStream os = new ByteBufOutputStream(buffer);
                JsonGenerator gen = JSON_FACTORY.createGenerator(os);
                response(gen, ip);
                gen.close();
                stream.write(ResponseHeaders.of(HttpStatus.OK, HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8));
                stream.write(HttpData.wrap(buffer));
            } catch (IOException e) {
                //handleStreamError(stream, e);
            } finally {
                stream.close();
            }
        });
        return HttpResponse.of(stream);
    }

    private void response(JsonGenerator gen, String ip) throws IOException {
        IPSeeker seeker = IPSeeker.getInstance();
        gen.writeStartObject();
        gen.writeStringField("ip", ip);
        gen.writeStringField("area", seeker.getArea(ip));
        gen.writeStringField("country", seeker.getCountry(ip));
        gen.writeEndObject();//end
    }

    @Get("/ipSeek")
    public HttpResponse query(ServiceRequestContext ctx) {
        String ip = ctx.clientAddress().getHostAddress();
        return query(ctx, ip);
    }
}
