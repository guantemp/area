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

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder ${YEAR}-${MONTH}-${DAY}
 */
public class Main {

    public static void main(String[] args) throws ServletException {
        ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo deploymentInfo = Servlets.deployment()
                .setClassLoader(Main.class.getClassLoader())
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
