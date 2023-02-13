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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2020-01-08
 */
@ServerEndpoint("/area/v1/websocket/batch")
public class AreaWebSocket {
    private static final Logger sysLogger = LoggerFactory.getLogger(AreaWebSocket.class);
    private Session session;

    @OnOpen
    public void open(Session session) {
        this.session = session;

        System.out.println("*** WebSocket opened from sessionId " + session.getId());
    }

    @OnMessage
    public void inMessage(String message) {
        System.out.println("*** WebSocket Received from " + this.session.getId() + ":" + message);
    }

    @OnClose
    public void end() {
        System.out.println("*** WebSocket closed from {} " + this.session.getId());
    }
}
