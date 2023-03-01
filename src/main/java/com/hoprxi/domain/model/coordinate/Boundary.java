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

import java.util.Objects;
import java.util.StringJoiner;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 builder 2023-02-08
 */
public class Boundary {
    private WGS84 min;
    private WGS84 max;
    private final WGS84 centre;

    public Boundary(WGS84 centre) {
        this.centre = Objects.requireNonNull(centre, "centre is required");
    }

    public Boundary(WGS84 centre, WGS84 min, WGS84 max) {
        this.centre = Objects.requireNonNull(centre, "centre is required");
        this.min = min;
        this.max = max;
    }

    public WGS84 getMin() {
        return min;
    }

    public WGS84 getMax() {
        return max;
    }

    public WGS84 getCentre() {
        return centre;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Boundary.class.getSimpleName() + "[", "]")
                .add("min=" + min)
                .add("max=" + max)
                .add("centre=" + centre)
                .toString();
    }
}
