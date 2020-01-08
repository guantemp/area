/*
 * Copyright (c) 2020. www.hoprxi.com All Rights Reserved.
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

package area.hoprxi.web;

import com.alibaba.excel.annotation.ExcelProperty;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK8.0
 * @version 0.0.1 2020-01-07
 */
public class AreaReadWriteModel {
    @ExcelProperty(value = "编号", index = 0)
    private String id;
    @ExcelProperty(value = "名称", index = 1)
    private String name;
    @ExcelProperty(value = "父级", index = 2)
    private String parentId;
    @ExcelProperty(value = "简称", index = 3)
    private String mergerName;
    @ExcelProperty(value = "别名", index = 4)
    private String alternative;
    @ExcelProperty(value = "别名简称", index = 5)
    private String mergerAlternative;
    @ExcelProperty(value = "经度", index = 6)
    private double latitude;
    @ExcelProperty(value = "纬度", index = 7)
    private double longitude;
    @ExcelProperty(value = "等级", index = 8)
    private int grade;
    @ExcelProperty(value = "排序", index = 9)
    private int sequence;
    @ExcelProperty(value = "邮编", index = 10)
    private String postcode;
    @ExcelProperty(value = "电话区号", index = 11)
    private String telephoneCodes;
}
