/*
 * Copyright (c) 2025. www.hoprxi.com All Rights Reserved.
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

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.3 builder 2025-08-19
 */
public abstract class Area {
    private final WGS84 location;
    private final Name name;
    private final String zipcode;
    private final String telephoneCode;
    protected int code;
    protected int parentCode;
    private final Level level;

    protected Area(int code, int parentCode, Name name, WGS84 location, String zipcode, String telephoneCode, Level level) {
        setCode(code);
        setParentCode(parentCode);
        this.name = Objects.requireNonNull(name, "name required");
        this.location = location;
        this.zipcode = zipcode;
        this.telephoneCode = telephoneCode;
        this.level = level;
    }

    protected abstract void setCode(int code);

    protected abstract void setParentCode(int parentCode);

    public int code() {
        return code;
    }

    public int parentCode() {
        return parentCode;
    }

    public WGS84 location() {
        return location;
    }

    public Name name() {
        return name;
    }

    public String zipcode() {
        return zipcode;
    }

    public String telephoneCode() {
        return telephoneCode;
    }

    public Level level() {
        return level;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        Area area = (Area) o;
        return code == area.code;
    }

    @Override
    public int hashCode() {
        return code;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Area.class.getSimpleName() + "[", "]")
                .add("code='" + code + "'")
                .add("parentCode='" + parentCode + "'")
                .add("name=" + name)
                .add("location=" + location)
                .add("zipcode='" + zipcode + "'")
                .add("telephoneCode='" + telephoneCode + "'")
                .toString();
    }

    public enum Level {
        COUNTRY, PROVINCE, CITY, COUNTY, TOWN;
    }
}

