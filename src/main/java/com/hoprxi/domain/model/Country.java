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

import com.hoprxi.domain.model.coordinate.Boundary;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2023-02-08
 */
public class Country extends Area {
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^\\d{3,3}$");

    public Country(String code, String parentCode, Name name, Boundary boundary) {
        super(code, parentCode, name, boundary);
    }

    public Country(String code, String parentCode, Name name, Boundary boundary, String postcode, String telephoneCode) {
        super(code, parentCode, name, boundary, postcode, telephoneCode);
    }

    @Override
    protected void setParentCode(String parentCode) {
        parentCode = Objects.requireNonNull(parentCode, "parentCode required").trim();
        if (!code.equals(parentCode))
            throw new IllegalArgumentException("Root, zone code must be consistent with parent zone code");
        this.parentCode = parentCode;
    }

    @Override
    protected void setCode(String code) {
        code = Objects.requireNonNull(code, "code required").trim();
        Matcher matcher = COUNTRY_PATTERN.matcher(code);
        if (!matcher.matches())
            throw new IllegalArgumentException("The code requires 3 digits");
        this.code = code;
    }
}
