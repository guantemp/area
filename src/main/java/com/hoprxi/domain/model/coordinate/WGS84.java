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
package com.hoprxi.domain.model.coordinate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hoprxi.domain.model.OutOfChinaException;

import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK21
 * @version 0.0.2 builder 2025-09-03
 * @param latitude public static final double A = 6378245.0;public static final double EE = 0.00669342162296594323;public static final double PI = 3.1415926535897932384626;
 */

public record WGS84(double longitude, double latitude) {
    /**
     * @param longitude
     * @param latitude
     */
    @JsonCreator
    public WGS84(@JsonProperty("longitude") double longitude, @JsonProperty("latitude") double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WGS84 wgs84)) return false;

        return Double.compare(latitude, wgs84.latitude) == 0 && Double.compare(longitude, wgs84.longitude) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(latitude);
        result = 31 * result + Double.hashCode(longitude);
        return result;
    }


    public BD09 toBD09() throws OutOfChinaException {
        GCJ02 gcj02 = toGCJ02();
        return gcj02.toBD09();
    }

    public GCJ02 toGCJ02() throws OutOfChinaException {
        if (Calculation.isOutOfChina(longitude, latitude))
            throw new OutOfChinaException("");
        double[] result = Calculation.calc(longitude, latitude);
        double retLon = longitude + result[0];
        double retLat = latitude + result[1];
        return new GCJ02(retLon, retLat);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WGS84.class.getSimpleName() + "[", "]")
                .add("latitude=" + latitude)
                .add("longitude=" + longitude)
                .toString();
    }
}
