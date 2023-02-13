/*
 * Copyright (c) 2023. www.hoprxi.com All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.hoprxi.web.filter;


import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2020-01-05
 */
@WebFilter(filterName = "crossDomainFilter", urlPatterns = "/v1/batch",
        initParams = {@WebInitParam(name = "allowOrigin", value = "http://127.0.0.1"),
                @WebInitParam(name = "allowMethods", value = "POST"),
                @WebInitParam(name = "allowCredentials", value = "true"),
                @WebInitParam(name = "allowHeaders", value = "Origin, X-Requested-With, Content-Type, Accept," +
                        "WG-App-Version, WG-Device-Id, WG-Network-Type, WG-Vendor, WG-OS-Type, WG-OS-Version, WG-Device-Model, WG-CPU, WG-Sid, WG-App-Id, WG-Token")})
public class CrossDomainFilter implements Filter {
    private String allowOrigin;
    private String allowMethods;
    private String allowCredentials;
    private String allowHeaders;
    private String exposeHeaders;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        allowOrigin = filterConfig.getInitParameter("allowOrigin");
        allowMethods = filterConfig.getInitParameter("allowMethods");
        allowCredentials = filterConfig.getInitParameter("allowCredentials");
        allowHeaders = filterConfig.getInitParameter("allowHeaders");
        exposeHeaders = filterConfig.getInitParameter("exposeHeaders");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (!allowOrigin.isEmpty()) {
            if (allowOrigin.equals("*")) {
                response.setHeader("Access-Control-Allow-Origin", allowOrigin);
            } else {
                List<String> allowOriginList = Arrays.asList(allowOrigin.split(","));
                if (allowOriginList != null && allowOriginList.size() > 0) {
                    String currentOrigin = request.getHeader("Origin");
                    if (allowOriginList.contains(currentOrigin)) {
                        response.setHeader("Access-Control-Allow-Origin", currentOrigin);
                    }
                }
            }
        }
        if (!allowMethods.isEmpty()) {
            response.setHeader("Access-Control-Allow-Methods", allowMethods);
        }
        if (!allowCredentials.isEmpty()) {
            response.setHeader("Access-Control-Allow-Credentials", allowCredentials);
        }
        if (!allowHeaders.isEmpty()) {
            response.setHeader("Access-Control-Allow-Headers", allowHeaders);
        }
        if (!exposeHeaders.isEmpty()) {
            response.setHeader("Access-Control-Expose-Headers", exposeHeaders);
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
