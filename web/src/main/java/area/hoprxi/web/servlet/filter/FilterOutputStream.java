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
import javax.servlet.WriteListener;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author <a href="mailto:myis1000@126.com">guan xiangHuang</a>
 * @version 1.0 2011-12-12
 * @since JDK6.0
 */
public class FilterOutputStream extends ServletOutputStream {
    private OutputStream os;

    /**
     * @param os
     */
    public FilterOutputStream(OutputStream os) {
        this.os = os;
    }

    @Override
    public void close() throws IOException {
        this.os.close();
    }

    @Override
    public void flush() throws IOException {
        this.os.flush();
    }

    @Override
    public boolean isReady() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setWriteListener(WriteListener arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void write(byte[] b) throws IOException {
        this.os.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        this.os.write(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        this.os.write(b);
    }
}
