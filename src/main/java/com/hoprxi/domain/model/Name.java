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


import salt.hoprxi.to.PinYin;

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.3 builder 2025-09-02
 */
public class Name {
    private final String abbreviation;
    private final String alias;
    private final String name;
    private final String mnemonic;

    public Name(String name,  String abbreviation, String mnemonic, String alias) {
        this.name = Objects.requireNonNull(name, "name required").trim();
        this.mnemonic = Objects.requireNonNull(mnemonic, "pinyin required").trim();
        this.abbreviation = abbreviation;
        this.alias = alias;
    }

    public Name(String name, String abbreviation, String alias) {
        this(name,  abbreviation, PinYin.toShortPinYing(name), alias);
    }

    public Name(String name, String abbreviation) {
        this(name, abbreviation, PinYin.toPinYing(abbreviation));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Name.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("mnemonic='" + mnemonic + "'")
                .add("abbreviation='" + abbreviation + "'")
                .add("alias='" + alias + "'")
                .toString();
    }

    public String abbreviation() {
        return abbreviation;
    }

    public String alias() {
        return alias;
    }

    public String name() {
        return name;
    }

    public String mnemonic() {
        return mnemonic;
    }
}
