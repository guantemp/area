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

package com.hoprxi.infrastructure.query;

import com.hoprxi.application.AreaQuery;
import com.hoprxi.application.AreaView;
import com.hoprxi.domain.model.Name;
import com.hoprxi.domain.model.coordinate.Boundary;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.PsqlAreaUtil;
import com.hoprxi.infrastructure.PsqlUtil;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import salt.hoprxi.cache.Cache;
import salt.hoprxi.cache.CacheFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-09
 */
public class PsqlAreaQuery implements AreaQuery {
    private static final Cache<String, AreaView> cache = CacheFactory.build("area");
    private static final Logger LOGGER = LoggerFactory.getLogger(PsqlAreaQuery.class);

    @Override
    public AreaView[] queryByName(String regularExpression) {
        try (Connection connection = PsqlUtil.getConnection()) {
            final String queryByNameSql = "select a1.code,a1.parent_code,a2.name::jsonb->>'name' parent_name,a1.name::jsonb->>'name' name,a1.name::jsonb->>'mnemonic' mnemonic,a1.name::jsonb->>'initials' initials,a1.name::jsonb->>'abbreviation' abbreviation,a1.name::jsonb->>'alternativeAbbreviation' alternativeAbbreviation,a1.zipcode,a1.telephone_code,a1.boundary::jsonb -> 0 center, a1.boundary::jsonb -> 1 min,a1.boundary::jsonb -> 2 max,a1.\"type\" from area a1\n" +
                    "inner join area a2 on a2.code = a1.parent_code\n" +
                    "where a1.name::jsonb ->> 'name' ~ ?\n" +
                    "union\n" +
                    "select a1.code,a1.parent_code,a2.name::jsonb->>'name' parent_name,a1.name::jsonb->>'name' name,a1.name::jsonb->>'mnemonic' mnemonic,a1.name::jsonb->>'initials' initials,a1.name::jsonb->>'abbreviation' abbreviation,a1.name::jsonb->>'alternativeAbbreviation' alternativeAbbreviation,a1.zipcode,a1.telephone_code,a1.boundary::jsonb -> 0 center, a1.boundary::jsonb -> 1 min,a1.boundary::jsonb -> 2 max,a1.\"type\" from area a1\n" +
                    "inner join area a2 on a2.code = a1.parent_code\n" +
                    "where a1.name::jsonb ->> 'mnemonic' ~ ?\n";
            PreparedStatement ps = connection.prepareStatement(queryByNameSql);
            ps.setString(1, regularExpression);
            ps.setString(2, regularExpression);
            ResultSet rs = ps.executeQuery();
            return transform(rs);
        } catch (SQLException | IOException e) {
            LOGGER.error("Can't rebuild area", e);
        }
        return new AreaView[0];
    }

    private AreaView[] transform(ResultSet rs) throws SQLException, IOException {
        List<AreaView> areaViews = new ArrayList<>();
        while (rs.next()) {
            areaViews.add(rebuild(rs));
        }
        return areaViews.toArray(new AreaView[0]);
    }

    @Override
    public AreaView query(String code) {
        code = Objects.requireNonNull(code, "code required").trim();
        AreaView area = cache.get(code);
        if (area != null)
            return area;
        try (Connection connection = PsqlUtil.getConnection()) {
            final String findSql = "select a1.code,a1.parent_code,a2.name::jsonb->>'name' parent_name,a1.name::jsonb->>'name' name,a1.name::jsonb->>'mnemonic' mnemonic,a1.name::jsonb->>'initials' initials,a1.name::jsonb->>'abbreviation' abbreviation,a1.name::jsonb->>'alternativeAbbreviation' alternativeAbbreviation,a1.zipcode,a1.telephone_code,a1.boundary::jsonb -> 0 center, a1.boundary::jsonb -> 1 min,a1.boundary::jsonb -> 2 max,a1.\"type\" from area a1\n" +
                    " inner join area a2 on a2.code = a1.parent_code where a1.code=? limit 1";
            PreparedStatement preparedStatement = connection.prepareStatement(findSql);
            preparedStatement.setString(1, code);
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                area = rebuild(rs);
                cache.put(code, area);
            }
            return area;
        } catch (SQLException | IOException e) {
            LOGGER.error("Can't rebuild area with (code = {})", code, e);
        }
        return null;
    }

    private AreaView rebuild(ResultSet rs) throws SQLException, IOException {
        AreaView areaView = null;
        String code = rs.getString("code");
        AreaView.ParentArea parentArea = new AreaView.ParentArea(rs.getString("parent_code"), rs.getString("parent_name"));
        Name name = new Name(rs.getString("name"), rs.getString("mnemonic"), (char) rs.getInt("initials"), rs.getString("abbreviation"), rs.getString("alternativeAbbreviation"));
        Boundary boundary = new Boundary(PsqlAreaUtil.toWgs84(rs.getString("center")), PsqlAreaUtil.toWgs84(rs.getString("min")), PsqlAreaUtil.toWgs84(rs.getString("max")));
        String zipcode = rs.getString("zipcode");
        String telephoneCode = rs.getString("telephone_code");
        areaView = new AreaView(code, parentArea, name, boundary, zipcode, telephoneCode, AreaView.Level.valueOf(rs.getString("type")));
        return areaView;
    }

    @Override
    public AreaView[] queryByJurisdiction(String code) {
        try (Connection connection = PsqlUtil.getConnection()) {
            final String queryByNameSql = "select a1.code,a1.parent_code,a2.name::jsonb->>'name' parent_name,a1.name::jsonb->>'name' name,a1.name::jsonb->>'mnemonic' mnemonic,a1.name::jsonb->>'initials' initials,a1.name::jsonb->>'abbreviation' abbreviation,a1.name::jsonb->>'alternativeAbbreviation' alternativeAbbreviation,a1.zipcode,a1.telephone_code,a1.boundary::jsonb -> 0 center, a1.boundary::jsonb -> 1 min,a1.boundary::jsonb -> 2 max,a1.\"type\" from area a1\n" +
                    "inner join area a2 on a2.code = a1.parent_code\n" +
                    "where a1.code != a1.parent_code and a1.parent_code = ?";
            PreparedStatement ps = connection.prepareStatement(queryByNameSql);
            ps.setString(1, code);
            ResultSet rs = ps.executeQuery();
            return transform(rs);
        } catch (SQLException | IOException e) {
            LOGGER.error("Can't rebuild area", e);
        }
        return new AreaView[0];
    }

    @Override
    public AreaView[] queryCountry() {
        try (Connection connection = PsqlUtil.getConnection()) {
            final String queryByNameSql = "select a1.code,a1.parent_code,a2.name::jsonb->>'name' parent_name,a1.name::jsonb->>'name' name,a1.name::jsonb->>'mnemonic' mnemonic,a1.name::jsonb->>'initials' initials,a1.name::jsonb->>'abbreviation' abbreviation,a1.name::jsonb->>'alternativeAbbreviation' alternativeAbbreviation,a1.zipcode,a1.telephone_code,a1.boundary::jsonb -> 0 center, a1.boundary::jsonb -> 1 min,a1.boundary::jsonb -> 2 max,a1.\"type\" from area a1\n" +
                    "inner join area a2 on a2.code = a1.parent_code\n" +
                    "where a1.code = a1.parent_code";
            PreparedStatement ps = connection.prepareStatement(queryByNameSql);
            ResultSet rs = ps.executeQuery();
            return transform(rs);
        } catch (SQLException | IOException e) {
            LOGGER.error("Can't rebuild area", e);
        }
        return new AreaView[0];
    }
}
