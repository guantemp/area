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

import com.hoprxi.domain.model.OutOfChinaException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xianghuang">guan xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.2 builder 2023-02-08
 */

public final class WGS84 {
    public static final double A = 6378245.0;
    public static final double EE = 0.00669342162296594323;
    public static final double PI = 3.1415926535897932384626;
    private double latitude;
    private double longitude;

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
        if (this == o) return true;
        if (!(o instanceof WGS84)) return false;

        WGS84 wgs84 = (WGS84) o;

        if (Double.compare(wgs84.latitude, latitude) != 0) return false;
        return Double.compare(wgs84.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(latitude);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(longitude);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }


    public BD09 toBD09() throws OutOfChinaException {
        GCJ02 gcj02 = toGCJ02();
        return gcj02.toBD09();
    }

    /**
     * @return
     * @throws OutOfChinaException
     */
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
