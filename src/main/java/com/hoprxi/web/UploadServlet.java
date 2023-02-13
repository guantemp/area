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

package com.hoprxi.web;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-29
 */
@WebServlet(urlPatterns = {"/v1/upload"}, name = "upload", asyncSupported = false, initParams = {
        @WebInitParam(name = "databaseName", value = "catalog")})
public class UploadServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().println("<html>");
        response.getWriter().println("<head>");
        response.getWriter().println("<title>文件上传</title>");
        response.getWriter().println("</head>");
        response.getWriter().println("<body>");
        response.getWriter().println("<form action=\"upload\" method=\"post\" enctype=\"multipart/form-data\">");
        response.getWriter().println("<input type=\"file\" name=\"file\"/>");
        response.getWriter().println("<input type=\"submit\" value=\"上传\"/>");
        response.getWriter().println("</form>");
        response.getWriter().println("</body>");
        response.getWriter().println("</html>");
        response.getWriter().close();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with,content-type");
        if (ServletFileUpload.isMultipartContent(request)) {//是否文件表单
            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setDefaultCharset("UTF-8");
            factory.setFileCleaningTracker(null);
            // Configure a repository (to ensure a secure temp location is used)

            //System.out.println(loader.getResource("tempdir").getPath());
            //factory.setRepository(new File(loader.getResource("tempdir").getPath()));
            //factory.setSizeThreshold(1024 * 1024);
            // Create a new file upload handler
            ServletFileUpload upload = new ServletFileUpload(factory);
            // Set overall request size constraint
            // upload.setSizeMax(100 * 1024 * 1024);
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                List<FileItem> items = upload.parseRequest(request);
                for (FileItem item : items) {
                    if (item.isFormField()) {//判断是否是文件流
                        String va = item.getString("UTF-8");
                        //	System.out.println(name+"="+va);
                        ///		request.setAttribute(name, value);
                    } else {
                        System.out.println(loader.getResource("conf").getPath());
                        final StringJoiner path = new StringJoiner(File.separator, loader.getResource("upload").getPath() + File.separator, "")
                                .add(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                .add(item.getName());
                        File uploadedFile = new File(path.toString());
                        File fileParent = uploadedFile.getParentFile();
                        if (!fileParent.exists()) {
                            fileParent.mkdirs();// 创建多个子目录区分类
                        }
                        uploadedFile.deleteOnExit();
                        uploadedFile.createNewFile();
                        item.write(uploadedFile);
                    }
                }
            } catch (FileUploadException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doOptions(req, resp);
        resp.setHeader("Access-Control-Allow-Credentials", "true");
        resp.setHeader("Access-Control-Allow-Origin", "http://127.0.0.1");
        resp.setHeader("Access-Control-Allow-Methods", "POST, GET");
        resp.setHeader("Access-Control-Allow-Headers", "x-requested-with,content-type");
    }
}
