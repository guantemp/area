/*
 * Copyright (c) 2019. www.foxtail.cc rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package area.hoprxi.core.domain.model;

import mi.hoprxi.to.PinYin;

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-27
 */
public class Name {
    private char initials;
    private String name;
    private String pinyin;
    private String mergerName;
    private String alternative;
    private String mergerAlternative;

    public Name(String name, String pinyin, char initials, String mergerName, String alternative, String mergerAlternative) {
        setName(name);
        setPinyin(pinyin);
        setInitials(initials);
        setMergerName(mergerName);
        this.alternative = alternative;
        this.mergerAlternative = mergerAlternative;
    }

    public Name(String name, String mergerName, String alternative, String mergerAlternative) {
        this(name, PinYin.toPinYing(name), PinYin.toPinYing(name).charAt(0), mergerName, alternative, mergerAlternative);
    }

    private void setMergerName(String mergerName) {
        this.mergerName = Objects.requireNonNull(mergerName, "mergerName required").trim();
    }

    private void setInitials(char initials) {
        this.initials = Objects.requireNonNull(initials, "initials required");
    }

    private void setPinyin(String pinyin) {
        this.pinyin = Objects.requireNonNull(pinyin, "pinyin required").trim();
    }

    private void setName(String name) {
        name = Objects.requireNonNull(name, "name required").trim();
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Name name1 = (Name) o;

        if (initials != name1.initials) return false;
        if (name != null ? !name.equals(name1.name) : name1.name != null) return false;
        if (pinyin != null ? !pinyin.equals(name1.pinyin) : name1.pinyin != null) return false;
        if (mergerName != null ? !mergerName.equals(name1.mergerName) : name1.mergerName != null) return false;
        if (alternative != null ? !alternative.equals(name1.alternative) : name1.alternative != null) return false;
        return mergerAlternative != null ? mergerAlternative.equals(name1.mergerAlternative) : name1.mergerAlternative == null;
    }

    @Override
    public int hashCode() {
        int result = initials;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (pinyin != null ? pinyin.hashCode() : 0);
        result = 31 * result + (mergerName != null ? mergerName.hashCode() : 0);
        result = 31 * result + (alternative != null ? alternative.hashCode() : 0);
        result = 31 * result + (mergerAlternative != null ? mergerAlternative.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Name.class.getSimpleName() + "[", "]")
                .add("initials=" + initials)
                .add("name='" + name + "'")
                .add("pinyin='" + pinyin + "'")
                .add("mergerName='" + mergerName + "'")
                .add("alternative='" + alternative + "'")
                .add("mergerAlternative='" + mergerAlternative + "'")
                .toString();
    }
}
