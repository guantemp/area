/*
 * Copyright (c) 2022. www.hoprxi.com All Rights Reserved.
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

package com.hoprxi.web.ip;

/**
 * @author swallow
 * @version 1.0 2011-10-28
 * @since JDK6.0
 */

public class IPEntry {
    public String area;
    public String beginIp;
    public String country;
    public String endIp;

    public IPEntry() {
        beginIp = endIp = country = area = "";
    }

    public String toString() {
        return this.area + "  " + this.country + "IP scale:" + this.beginIp
                + "-" + this.endIp;
    }
}
