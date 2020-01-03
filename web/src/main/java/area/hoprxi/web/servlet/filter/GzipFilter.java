/*
 * Copyright (c) 2019. www.foxtail.cc rights Reserved.
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

import mi.hoprxi.util.NumberHelper;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2019-12-29
 */
@WebFilter(urlPatterns = {"*.html"}, filterName = "gzipFilter", asyncSupported = true, initParams = {
        @WebInitParam(name = "gzip", value = "true")})
public class GzipFilter implements Filter {
    private class GZIPFilterAsync implements Runnable {
        private ByteArrayOutputStream baos;
        private AsyncContext ctx;
        private GZIPOutputStream out;

        public GZIPFilterAsync(AsyncContext ctx, ByteArrayOutputStream baos, GZIPOutputStream out) {
            this.ctx = ctx;
            this.baos = baos;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                HttpServletResponse response = (HttpServletResponse) ctx.getResponse();
                response.addHeader("Content-Encoding", "gzip");
                response.flushBuffer();
                out.close();

                byte[] bytes = baos.toByteArray();
                response.setContentLength(bytes.length);
                ServletOutputStream sos = response.getOutputStream();
                sos.write(bytes);
                ctx.complete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private FilterConfig filterConfig;
    private boolean gzip = false;

    @Override
    public void destroy() {
        filterConfig = null;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (this.isGzip(request)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzout = new GZIPOutputStream(baos);
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            GenericResponseWrapper wrapper = new GenericResponseWrapper(httpServletResponse, gzout);
            chain.doFilter(request, wrapper);
            wrapper.addHeader("Content-Encoding", "gzip");
            wrapper.flushBuffer();
            gzout.close();

            byte[] bytes = baos.toByteArray();
            response.setContentLength(bytes.length);
            ServletOutputStream sos = response.getOutputStream();
            sos.write(bytes);
            // AsyncContext ctx = request.startAsync(request, wrapper);
            // ctx.start(new GZIPFilterAsync(ctx, baos, gzout));
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
        if (null != this.filterConfig) {
            gzip = NumberHelper.booleanOf(this.filterConfig.getInitParameter("gzip"));
        }
    }

    /**
     * @param request
     * @return
     */
    private boolean isGzip(ServletRequest request) {
        String support = ((HttpServletRequest) request).getHeader("Accept-Encoding");
        if (null != support && gzip && (support.indexOf("gzip") != -1 || support.indexOf("x-gzip") != -1)) {
            return true;
        }
        return false;
    }
}
