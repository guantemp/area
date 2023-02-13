/*
 * Copyright (c) 2022. www.hoprxi.com All Rights Reserved.
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

package com.hoprxi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hoprxi.web.ip.IPEntry;
import com.hoprxi.web.ip.IPSeeker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2018/10/28
 */
@WebServlet(value = "/v1/ipSeeker/*", name = "ipSeeker", asyncSupported = true)
public class IpSeekerServlet extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(IpSeekerServlet.class);
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        IPSeeker seeker = IPSeeker.getInstance();

        IPEntry entry = new IPEntry();
        entry.beginIp = request.getRemoteAddr();
        entry.area = seeker.getArea(entry.beginIp);
        entry.country = seeker.getCountry(entry.beginIp);

        ObjectMapper mapper = new ObjectMapper();
        PrintWriter writer = response.getWriter();
        writer.print(mapper.writeValueAsString(entry));
        writer.flush();

        // AsyncContext ctx = request.startAsync();
        // new Thread(new IPAsync(ctx)).start();
    }

    private class IPAsync implements Runnable {
        private AsyncContext ctx;

        public IPAsync(AsyncContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            IPSeeker seeker = IPSeeker.getInstance();

            IPEntry entry = new IPEntry();
            entry.beginIp = ctx.getRequest().getRemoteAddr();
            entry.area = seeker.getArea(entry.beginIp);
            entry.country = seeker.getCountry(entry.beginIp);

            try {
                ObjectMapper mapper = new ObjectMapper();
                PrintWriter writer = ctx.getResponse().getWriter();
                writer.print(mapper.writeValueAsString(entry));
                writer.flush();
                writer.close();
                ctx.complete();
            } catch (IOException e) {
                e.printStackTrace();
                logger.error("error", e);
            }
        }
    }
}
