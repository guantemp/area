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

import com.arangodb.entity.DocumentField;
import com.arangodb.velocypack.annotations.Expose;
import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 2019-12-27
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public abstract class Area {
    @DocumentField(DocumentField.Type.KEY)
    protected String id;
    @Expose(serialize = false, deserialize = false)
    protected String parentId;
    @Expose(serialize = false, deserialize = false)
    private WGS84 wgs84;
    private Name name;
    private String postcode;
    private String telephoneCodes;
    private byte sequence;

    public Area(String id, String parentId, Name name, String postcode, byte sequence, WGS84 wgs84) {
        setId(id);
        setParentId(parentId);
        setName(name);
        setPostcode(postcode);
        this.sequence = sequence;
        setWgs84(wgs84);
    }

    public Area(String id, String parentId, Name name, String postcode, String telephoneCodes, byte sequence, WGS84 wgs84) {
        setId(id);
        setParentId(parentId);
        setName(name);
        setPostcode(postcode);
        this.telephoneCodes = telephoneCodes;
        this.sequence = sequence;
        setWgs84(wgs84);
    }

    private void setWgs84(WGS84 wgs84) {
        this.wgs84 = Objects.requireNonNull(wgs84, "wgs84 required");
    }

    private void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    private void setName(Name name) {
        this.name = Objects.requireNonNull(name, "name required");
    }

    protected abstract void setParentId(String parentId);

    protected abstract void setId(String id);

    public WGS84 wgs84() {
        return wgs84;
    }

    public Name name() {
        return name;
    }

    public String postcode() {
        return postcode;
    }

    public byte sequence() {
        return sequence;
    }

    public String id() {
        return id;
    }

    public String parentId() {
        return parentId;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Area area = (Area) o;

        return id != null ? id.equals(area.id) : area.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Area.class.getSimpleName() + "[", "]")
                .add("id='" + id + "'")
                .add("parentId='" + parentId + "'")
                .add("wgs84=" + wgs84)
                .add("name=" + name)
                .add("postcode='" + postcode + "'")
                .add("sort=" + sequence)
                .toString();
    }
}

