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
package com.hoprxi.domain.model.coordinate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2023-02-08
 */

public final class BD09 {
    private final double latitude;
    private final double longitude;

    /**
     * @param longitude
     * @param latitude
     */
    @JsonCreator
    public BD09(@JsonProperty("longitude") double longitude, @JsonProperty("latitude") double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public WGS84 toWGS84() {
        GCJ02 gcj02 = toGCJ02();
        return gcj02.toWGS84();
    }

    public GCJ02 toGCJ02() {
        double x = longitude - 0.0065, y = latitude - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * Calculation.PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * Calculation.PI);
        double retLat = z * Math.sin(theta);
        double retLon = z * Math.cos(theta);
        return new GCJ02(retLon, retLat);
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }
}
