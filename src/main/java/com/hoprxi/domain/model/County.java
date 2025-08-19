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

package com.hoprxi.domain.model;

import com.hoprxi.domain.model.coordinate.WGS84;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2023-02-08
 */
public class County extends Area {
    private static final Pattern COUNTY_PATTERN = Pattern.compile("^\\d{6,6}$");

    public County(int code, int parentCode, Name name, WGS84 wgs84) {
        this(code, parentCode, name, wgs84, null, null);
    }

    public County(int code, int parentCode, Name name, WGS84 wgs84, String postcode, String telephoneCode) {
        super(code, parentCode, name, wgs84, postcode, telephoneCode, Level.COUNTY);
    }

    @Override
    protected void setParentCode(int parentCode) {
        Matcher matcher = COUNTY_PATTERN.matcher(String.valueOf(parentCode));
        if (!matcher.matches()) throw new IllegalArgumentException("Parent code requires 6 digits");
        this.parentCode = parentCode;
    }

    @Override
    protected void setCode(int code) {
        Matcher matcher = COUNTY_PATTERN.matcher(String.valueOf(code));
        if (!matcher.matches()) throw new IllegalArgumentException("The code requires 6 digits");
        this.code = code;
    }
}
