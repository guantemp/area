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

package com.hoprxi.infrastructure.persistence;

import com.fasterxml.jackson.core.*;
import com.hoprxi.domain.model.*;
import com.hoprxi.domain.model.coordinate.Boundary;
import com.hoprxi.domain.model.coordinate.WGS84;
import com.hoprxi.infrastructure.PsqlAreaUtil;
import com.hoprxi.infrastructure.PsqlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-09
 */
public class PsqlAreaRepository implements AreaRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsqlAreaRepository.class);
    private final JsonFactory jasonFactory = JsonFactory.builder().build();

    /**
     * @param code
     * @return
     */
    @Override
    public Area find(String code) {
        code = Objects.requireNonNull(code, "code required").trim();
        try (Connection connection = PsqlUtil.getConnection()) {
            final String findSql = "select code,parent_code,name::jsonb->>'name' name,name::jsonb->>'mnemonic' mnemonic,name::jsonb->>'initials' initials,name::jsonb->>'abbreviation' abbreviation,name::jsonb->>'alternativeAbbreviation' alternativeAbbreviation," +
                    "zipcode,telephone_code,boundary::jsonb -> 0 center, boundary::jsonb -> 1 min,boundary::jsonb -> 2 max,\"type\" from area where code=? limit 1";
            PreparedStatement preparedStatement = connection.prepareStatement(findSql);
            preparedStatement.setString(1, code);
            ResultSet rs = preparedStatement.executeQuery();
            return rebuild(rs);
        } catch (SQLException | IOException e) {
            LOGGER.error("Can't rebuild area with (code = {})", code, e);
        }
        return null;
    }

    private Area rebuild(ResultSet rs) throws SQLException, IOException {
        Area area = null;
        if (rs.next()) {
            String code = rs.getString("code");
            String parentCode = rs.getString("parent_code");
            Name name = new Name(rs.getString("name"), rs.getString("mnemonic"), (char) rs.getInt("initials"), rs.getString("abbreviation"), rs.getString("alternativeAbbreviation"));
            Boundary boundary = new Boundary(PsqlAreaUtil.toWgs84(rs.getString("center")), PsqlAreaUtil.toWgs84(rs.getString("min")), PsqlAreaUtil.toWgs84(rs.getString("max")));
            String zipcode = rs.getString("zipcode");
            String telephoneCode = rs.getString("telephone_code");
            String type = rs.getString("type");
            switch (type) {
                case "PROVINCE":
                    area = new Province(code, parentCode, name, boundary, zipcode, telephoneCode);
                    break;
                case "COUNTRY":
                    area = new Country(code, parentCode, name, boundary, zipcode, telephoneCode);
                    break;
                case "CITY":
                    area = new City(code, parentCode, name, boundary, zipcode, telephoneCode);
                    break;
                case "COUNTY":
                    area = new County(code, parentCode, name, boundary, zipcode, telephoneCode);
                    break;
                case "TOWN":
                    area = new Town(code, parentCode, name, boundary, zipcode, telephoneCode);
                    break;
            }
        }
        return area;
    }


    @Override
    public void save(Area area) {
        final String insertRoot = "insert into area (code,parent_code,name,zipcode,telephone_code,boundary,\"type\") values (?,?,?::jsonb,?,?,?::jsonb,CAST(? AS area_type)) ";
        try (Connection connection = PsqlUtil.getConnection()) {
            PreparedStatement ps = connection.prepareStatement(insertRoot);
            ps.setString(1, area.code());
            ps.setString(2, area.parentCode());
            ps.setString(3, PsqlAreaUtil.toJson(area.name()));
            ps.setString(4, area.zipcode());
            ps.setString(5, area.telephoneCode());
            ps.setString(6, PsqlAreaUtil.toJson(area.boundary()));
            //如果值area.getClass().getSimpleName()不是enum类型，要在插入语句中加入 CAST（？ AS enum_type)
            //如果是java enum类型，使用 enum.name（）填充即可
            ps.setString(7, area.getClass().getSimpleName().toUpperCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Can't save area{}", area, e);
        }
    }

    /**
     * @param code
     */
    @Override
    public void delete(String code) {
        try (Connection connection = PsqlUtil.getConnection()) {
            final String removeSql = "delete from area where code=?";
            PreparedStatement preparedStatement = connection.prepareStatement(removeSql);
            preparedStatement.setString(1, code);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Can't delete brand(code={})", code, e);
        }
    }
}
