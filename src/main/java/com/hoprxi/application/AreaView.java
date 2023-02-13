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

package com.hoprxi.application;

import com.hoprxi.domain.model.Name;
import com.hoprxi.domain.model.coordinate.Boundary;

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-10
 */
public class AreaView {
    private final ParentArea parentArea;
    private final String code;
    private Boundary boundary;
    private final Name name;
    private String zipcode;
    private String telephoneCode;
    private Level level;

    public AreaView(String code, Name name, ParentArea parentArea) {
        this.parentArea = parentArea;
        this.code = code;
        this.name = name;
    }

    public AreaView(String code, ParentArea parentArea, Name name, Boundary boundary, String zipcode, String telephoneCode, Level level) {
        this.parentArea = parentArea;
        this.code = code;
        this.boundary = boundary;
        this.name = name;
        this.zipcode = zipcode;
        this.telephoneCode = telephoneCode;
        this.level = level;
    }

    public ParentArea parentAreaView() {
        return parentArea;
    }

    public String code() {
        return code;
    }

    public Boundary boundary() {
        return boundary;
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
        if (!(o instanceof AreaView)) return false;

        AreaView areaView = (AreaView) o;

        return Objects.equals(code, areaView.code);
    }

    @Override
    public int hashCode() {
        return code != null ? code.hashCode() : 0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AreaView.class.getSimpleName() + "[", "]")
                .add("parentAreaView=" + parentArea)
                .add("code='" + code + "'")
                .add("boundary=" + boundary)
                .add("name=" + name)
                .add("zipcode='" + zipcode + "'")
                .add("telephoneCode='" + telephoneCode + "'")
                .toString();
    }

    public Level level() {
        return level;
    }

    public enum Level {
        CITY, COUNTRY, COUNTY, TOWN, PROVINCE
    }

    public static class ParentArea {
        private final String code;
        private final String name;

        public ParentArea(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public String code() {
            return code;
        }

        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ParentArea.class.getSimpleName() + "[", "]")
                    .add("code='" + code + "'")
                    .add("name='" + name + "'")
                    .toString();
        }
    }
}
