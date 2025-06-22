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

package com.hoprxi.infrastructure;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2022-08-25
 */
public class PsqlUtil {
    private static final Config config;
    private static HikariDataSource hikariDataSource;

    static {
        config = ConfigFactory.load("area");
    }

    public static Connection getConnection(String databaseName) throws SQLException {
        if (hikariDataSource == null) {
            List<? extends Config> writes = config.getConfigList("write");
            Config write = writes.get(0);
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setDataSourceClassName(write.getString("hikari.dataSourceClassName"));
            String entry = write.getString("host") + ":" + write.getString("port");
            hikariConfig.setUsername(DecryptUtil.decrypt(entry, write.getString("user")));
            hikariConfig.setPassword(DecryptUtil.decrypt(entry, write.getString("password")));
            hikariConfig.addDataSourceProperty("serverName", write.getString("host"));
            hikariConfig.addDataSourceProperty("portNumber", write.getInt("port"));
            hikariConfig.addDataSourceProperty("databaseName", databaseName);
            hikariConfig.addDataSourceProperty("logWriter", new PrintWriter(System.out));
            //System.out.println(write.hasPath("hikari.maximumPoolSize") ? write.getInt("hikari.maximumPoolSize") : Runtime.getRuntime().availableProcessors());
            hikariConfig.setMaximumPoolSize(write.hasPath("hikari.maximumPoolSize") ? write.getInt("hikari.maximumPoolSize") : Runtime.getRuntime().availableProcessors() * 2);

            //hikariConfig.addDataSourceProperty("cachePreStmts", "true"); // Enable Prepared Statement caching
            //hikariConfig.addDataSourceProperty("prepStmtCacheSize", "25"); // How many PS cache, default: 25
            //hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            //hikariConfig.addDataSourceProperty("useServerPrepStmts", "true"); // If supported use PS server-side
            //hikariConfig.addDataSourceProperty("useLocalSessionState", "true"); // Enable setAutoCommit
            //hikariConfig.addDataSourceProperty("useLocalTransactionState", "true"); // Enable commit/rollbacks

            hikariDataSource = new HikariDataSource(hikariConfig);
        }
        return hikariDataSource.getConnection();
    }

    public static Connection getConnection() throws SQLException {
        String databaseName = config.hasPath("databaseName") ? config.getString("databaseName") : "area";
        return getConnection(databaseName);
    }

    public static void release(Connection connection) {
        if (hikariDataSource == null)
            return;
        hikariDataSource.evictConnection(connection);
    }
}
