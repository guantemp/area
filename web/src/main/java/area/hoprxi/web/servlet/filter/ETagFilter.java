/*
 * Copyright (c) 2019. www.hoprxi.com rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package area.hoprxi.web.servlet.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * @author <a href="mailto:myis1000@126.com">guan xiangHuang</a>
 * @version 1.0 2011-12-12
 * @since JDK6.0
 */
@WebFilter(urlPatterns = {"*.html"}, filterName = "ETagFilter", asyncSupported = true)
public class ETagFilter implements Filter {
    /*
     * private class ETagFilterAsync implements Runnable { private AsyncContext
     * ctx; private ByteArrayOutputStream baos;
     *
     * public ETagFilterAsync(AsyncContext ctx, ByteArrayOutputStream baos) {
     * this.ctx = ctx; this.baos = baos; }
     *
     * @Override public void run() { try { HttpServletResponse response =
     * (HttpServletResponse) ctx .getResponse(); response.flushBuffer();
     *
     * byte[] bytes = baos.toByteArray(); String ETag = getETag(bytes);
     * //System.out.println(ETag); response.setHeader("ETag", ETag);
     * HttpServletRequest httpRequest = (HttpServletRequest) ctx .getRequest();
     * String previousETag = httpRequest.getHeader("If-None-Match"); if (null !=
     * previousETag && previousETag.equals(ETag)) {
     * response.sendError(HttpServletResponse.SC_NOT_MODIFIED);
     * response.setHeader("Last-Modified",
     * httpRequest.getHeader("If-Modified-Since")); } else {
     * response.setDateHeader("Last-Modified", System.currentTimeMillis());
     * ServletOutputStream sos = response.getOutputStream(); sos.write(bytes); }
     * ctx.complete(); } catch (IOException e) { // TODO Auto-generated catch
     * block e.printStackTrace(); } } }
     */

    @Override
    public void destroy() {
        // TODO Auto-generated method stub

    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
        // TODO Auto-generated method stub

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        GenericResponseWrapper wrapper = new GenericResponseWrapper(
                httpResponse, baos);
        chain.doFilter(request, wrapper);
        wrapper.flushBuffer();
        // AsyncContext ctx = request.startAsync(request, wrapper);
        // ctx.start(new ETagFilterAsync(ctx,baos));

        byte[] bytes = baos.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(bytes);
        String ETag = "\"" + crc.getValue() + "\"";
        httpResponse.setHeader("ETag", ETag);
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String previousETag = httpRequest.getHeader("If-None-Match");
        if (null != previousETag && previousETag.equals(ETag)) {
            httpResponse.sendError(HttpServletResponse.SC_NOT_MODIFIED);
            httpResponse.setHeader("Last-Modified",
                    httpRequest.getHeader("If-Modified-Since"));
        } else {
            httpResponse.setDateHeader("Last-Modified",
                    System.currentTimeMillis());
            response.setContentLength(bytes.length);
            ServletOutputStream sos = httpResponse.getOutputStream();
            sos.write(bytes);
        }
    }
}
