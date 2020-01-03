/*
 * Copyright (c) 2019. www.hoprxi.com rights Reserved.
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

import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-27
 */
public class Country extends Area {
    private static final Pattern COUNTRY_PATTERN = Pattern.compile("^\\d{3,3}$");

    public Country(String id, String parentId, Name name, String postcode, byte sort, WGS84 wgs84) {
        super(id, parentId, name, postcode, sort, wgs84);
    }

    @Override
    protected void setParentId(String parentId) {
        parentId = Objects.requireNonNull(parentId, "parentId required").trim();
        if (!this.id.equals(parentId))
            throw new IllegalArgumentException("");
        this.parentId = parentId;
    }

    @Override
    protected void setId(String id) {
        id = Objects.requireNonNull(id, "id required").trim();
        Matcher matcher = COUNTRY_PATTERN.matcher(id);
        if (!matcher.matches())
            throw new IllegalArgumentException("");
        this.id = id;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Country.class.getSimpleName() + "[", "]")
                .add("super='" + super.toString() + "'")
                .add("id='" + id + "'")
                .add("parentId='" + parentId + "'")
                .toString();
    }
}
