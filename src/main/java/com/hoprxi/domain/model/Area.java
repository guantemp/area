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

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2023-02-08
 */
public abstract class Area {
    protected String code;
    protected String parentCode;
    private final WGS84 location;
    private final Name name;
    private final String zipcode;
    private final String telephoneCode;

    public Area(String code, String parentCode, Name name, WGS84 location) {
        this(code, parentCode, name, location, "", "");
    }

    public Area(String code, String parentCode, Name name, WGS84 location, String zipcode, String telephoneCode) {
        setCode(code);
        setParentCode(parentCode);
        this.name = Objects.requireNonNull(name, "name required");
        this.location = location;
        this.zipcode = zipcode;
        this.telephoneCode = telephoneCode;
    }

    protected abstract void setCode(String code);

    protected abstract void setParentCode(String parentCode);

    public String code() {
        return code;
    }

    public String parentCode() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Area)) return false;

        Area area = (Area) o;

        return Objects.equals(code, area.code);
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Area.class.getSimpleName() + "[", "]")
                .add("code='" + code + "'")
                .add("parentCode='" + parentCode + "'")
                .add("location=" + location)
                .add("name=" + name)
                .add("zipcode='" + zipcode + "'")
                .add("telephoneCode='" + telephoneCode + "'")
                .toString();
    }
}

