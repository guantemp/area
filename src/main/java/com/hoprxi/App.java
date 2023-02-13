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

package com.hoprxi;

import com.hoprxi.web.AreasServlet;
import com.hoprxi.web.IpSeekerServlet;
import com.hoprxi.web.UploadServlet;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;

import javax.servlet.ServletException;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws ServletException {
        ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo deploymentInfo = Servlets.deployment()
                .setClassLoader(App.class.getClassLoader())
                .setContextPath("/")
                .setDeploymentName("area.war")
                .addServlets(
                        Servlets.servlet("AreasServlet", AreasServlet.class)
                                .addMapping("/v1/areas/*"),
                        Servlets.servlet("UploadServlet", UploadServlet.class)
                                .addMapping("/v1/upload"),
                        Servlets.servlet("ipSeeker", IpSeekerServlet.class)
                                .addMapping("/v1/ip/*")
                );
                        /*
                        Servlets.servlet("itemServlet", AreaWebSocket.class)
                                .addInitParam("database", "arangodb")
                                .addInitParam("databaseName", "catalog")
                                .addMapping("/v1/items/*"));
                                */
        DeploymentManager manager = container.addDeployment(deploymentInfo);
        manager.deploy();
        PathHandler path = Handlers.path(Handlers.redirect("/"))
                .addPrefixPath(deploymentInfo.getContextPath(), manager.start());

        Undertow server = Undertow.builder()
                .addHttpListener(8081, "0.0.0.0")
                .setHandler(path)
                .build();
        server.start();
    }
}
