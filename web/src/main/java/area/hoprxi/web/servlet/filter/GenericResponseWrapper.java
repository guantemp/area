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
package area.hoprxi.web.servlet.filter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;

/***
 * @author <a href=
 *         "mailto:myis1000@126.com?subject=about%20cc.foxtail.areas.servlet.filter.GenericResponseWrapper.java">guan
 *         xiangHuan</a>
 * @since JDK8.0
 * @version 0.0.1 builder 20170426
 */

public class GenericResponseWrapper extends HttpServletResponseWrapper implements Serializable {
    private static final long serialVersionUID = 1L;
    private FilterOutputStream fos;
    private PrintWriter printWriter;

    public GenericResponseWrapper(HttpServletResponse response, OutputStream os) {
        super(response);
        fos = new FilterOutputStream(os);
    }

    @Override
    public void flushBuffer() throws IOException {
        if (null != printWriter) {
            printWriter.flush();
        } else if (null != fos) {
            fos.flush();
        }
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return fos;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (null == printWriter) {
            printWriter = new PrintWriter(new OutputStreamWriter(this.getOutputStream(), this.getCharacterEncoding()));
        }
        return printWriter;
    }
}
