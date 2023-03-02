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
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.FileCleanerCleanup;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileCleaningTracker;
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
import java.net.URL;
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
            ServletContext servletContext = getServletContext();
            FileCleaningTracker fileCleaningTracker
                    = FileCleanerCleanup.getFileCleaningTracker(servletContext);
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setDefaultCharset("UTF-8");
            factory.setFileCleaningTracker(fileCleaningTracker);
            // Configure a repository (to ensure a secure temp location is used)
            File tempDir = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
            System.out.println("tempDir:" + tempDir.toString());
            //超过4*1024*1kb(4MB)后写入临时文件
            factory.setSizeThreshold(4 * 1024 * 1024);
            factory.setRepository(tempDir);
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
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                System.out.println(loader.getResource(""));
                List<FileItem> items = upload.parseRequest(request);
                for (FileItem item : items) {
                    if (item.isFormField()) {//判断是否是文件流
                        String va = item.getString("UTF-8");
                        //	System.out.println(name+"="+va);
                        ///		request.setAttribute(name, value);
                    } else {
                        System.out.println(item.getName().substring(0, item.getName().lastIndexOf(".")));
                        String filepath = UploadServlet.class.getResource("/").toExternalForm();
                        filepath = filepath.substring(0, filepath.lastIndexOf("/"));
                        System.out.println(filepath.substring(0, filepath.lastIndexOf("/")));

                        LOGGER.info(UploadServlet.class.getResource("/").getFile());
                        String filename = item.getName();
                        if (filename.lastIndexOf(".") == -1) {
                            filename = "";
                        }
                        filename = filename.substring(filename.lastIndexOf("."));
                        final StringJoiner path = new StringJoiner("/", UploadServlet.class.getResource("/").toExternalForm(), "")
                                .add(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                .add(UUID.randomUUID() + filename);
                        LOGGER.info(path.toString());
                        System.out.println(new URL(path.toString()).toExternalForm());
                        File uploadedFile = new File(new URI(path.toString()));
                        File fileParent = uploadedFile.getParentFile();
                        if (!fileParent.exists()) {
                            fileParent.mkdirs();// 创建多个子目录区分类
                        }
                        uploadedFile.deleteOnExit();
                        //uploadedFile.createNewFile();从临时文件拷贝过来的不能新建，报文件已存在异常，小于4MB的直接在内存里面的可以使用不报异常
                        item.write(uploadedFile);
                        item.delete();
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
