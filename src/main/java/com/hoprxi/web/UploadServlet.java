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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-29
 */
@WebServlet(urlPatterns = {"/v1/upload"}, name = "upload", initParams = {@WebInitParam(name = "UPLOAD_DIRECTORY", value = "upload"),
        @WebInitParam(name = "MEMORY_THRESHOLD", value = "4194304"), @WebInitParam(name = "MAX_FILE_SIZE", value = "67108864")})
public class UploadServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadServlet.class);
    private final JsonFactory jasonFactory = JsonFactory.builder().build();


    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        /*
        Enumeration<String> names = config.getInitParameterNames();
        while (names.hasMoreElements())
            System.out.println(names.nextElement());
        System.out.println(config.getInitParameter("MEMORY_THRESHOLD"));
         */
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().println("<html>");
        response.getWriter().println("<head>");
        response.getWriter().println("<title>文件上传</title>");
        response.getWriter().println("</head>");
        response.getWriter().println("<body>");
        response.getWriter().println("<form action=\"upload\" method=\"post\" enctype=\"multipart/form-data\">");
        response.getWriter().println("<p><input type=\"checkbox\" name=\"randomFileName\"/>重命名文件</p>");
        response.getWriter().println("<p><input type=\"file\" name=\"file\"/></p>");
        response.getWriter().println("<input type=\"submit\" value=\"上传\"/>");
        response.getWriter().println("</form>");
        response.getWriter().println("</body>");
        response.getWriter().println("</html>");
        response.getWriter().close();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        JsonGenerator generator = jasonFactory.createGenerator(response.getOutputStream(), JsonEncoding.UTF8).useDefaultPrettyPrinter();
        StringJoiner relativePath = new StringJoiner("/", request.getScheme() + "://" + request.getServerName(), "").add("/images");
        if (!ServletFileUpload.isMultipartContent(request)) {//是否文件表单
            generator.writeStartObject();
            generator.writeStringField("status", "fail");
            generator.writeStringField("message", "表单必须包含 enctype=multipart/form-data");
            generator.writeEndObject();
        } else {
            // Create a factory for disk-based file items
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setDefaultCharset("UTF-8");
            factory.setFileCleaningTracker(null);
            // Configure a repository (to ensure a secure temp location is used)
            ServletContext servletContext = this.getServletConfig().getServletContext();
            File repository = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
            //超过4*1024*1kb(4MB)后写入临时文件
            factory.setSizeThreshold(4 * 1024 * 1024);
            factory.setRepository(repository);
            ServletFileUpload upload = new ServletFileUpload(factory);
            upload.setProgressListener(new ProgressListener() {
                private long megaBytes = -1;

                public void update(long pBytesRead, long pContentLength, int pItems) {
                    long mBytes = pBytesRead / 1000000;
                    if (megaBytes == mBytes) {
                        return;
                    }
                    megaBytes = mBytes;
                    System.out.println("We are currently reading item " + pItems);
                    if (pContentLength == -1) {
                        System.out.println("So far, " + pBytesRead + " bytes have been read.");
                    } else {
                        System.out.println("So far, " + pBytesRead + " of " + pContentLength
                                + " bytes have been read.");
                    }
                }
            });
            // 上传文件的最大阀值64*1024kb=64mb
            // upload.setSizeMax(64 * 1024 * 1024);
            boolean random = false;
            try {
                List<FileItem> items = upload.parseRequest(request);
                for (FileItem item : items) {
                    if (item.isFormField()) {//判断是否是文件流
                        if ("randomFileName".equals(item.getFieldName()) && "on".equals(item.getString("UTF-8")))
                            random = true;
                    } else {
                        String filepath = UploadServlet.class.getResource("/").toExternalForm();
                        String[] sss = filepath.split("/");
                        StringJoiner joiner = new StringJoiner("/", "", "/");
                        for (int i = 0, j = sss.length - 1; i < j; i++) {
                            joiner.add(sss[i]);
                        }
                        joiner.add("upload");
                        String fileName = item.getName();
                        String folder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        relativePath.add(folder);
                        final StringJoiner path = new StringJoiner("/", joiner.toString(), "")
                                .add(folder);
                        if (random) {
                            String extension = fileName.lastIndexOf(".") == -1 ? "" : fileName.substring(fileName.lastIndexOf("."));
                            String randomName = UUID.randomUUID() + extension;
                            relativePath.add(randomName);
                            path.add(randomName);
                        } else {
                            relativePath.add(fileName);
                            path.add(fileName);
                        }
                        System.out.println(path);
                        System.out.println(relativePath);
                        LOGGER.info(path.toString());

                        File uploadedFile = new File(new URI(path.toString()));
                        File fileParent = uploadedFile.getParentFile();
                        if (!fileParent.exists()) {
                            fileParent.mkdirs();// 创建多个子目录区分类
                        }
                        uploadedFile.deleteOnExit();
                        //uploadedFile.createNewFile();从临时文件拷贝过来的不能新建，报文件已存在异常，小于4MB的直接在内存里面的可以使用不报异常
                        item.write(uploadedFile);//写入文件
                        item.delete();//删除临时文件
                        //InputStream uploadedStream = item.getInputStream();
                        // uploadedStream.close();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            generator.writeStartObject();
            generator.writeStringField("status", "success");
            generator.writeStringField("url", relativePath.toString());
            generator.writeEndObject();
        }
        generator.flush();
        generator.close();
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
