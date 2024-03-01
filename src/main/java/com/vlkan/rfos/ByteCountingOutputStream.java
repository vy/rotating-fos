/*
 * Copyright 2018-2024 Volkan Yazıcı <volkan@yazi.ci>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */

package com.vlkan.rfos;

import java.io.IOException;
import java.io.OutputStream;

class ByteCountingOutputStream extends OutputStream {

    private final OutputStream parent;

    private long size;

    ByteCountingOutputStream(OutputStream parent, long size) {
        this.parent = parent;
        this.size = size;
    }

    long size() {
        return size;
    }

    @Override
    public void write(int b) throws IOException {
        parent.write(b);
        size += 1;
    }

    @Override
    public void write(byte[] b) throws IOException {
        parent.write(b);
        size += b.length;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        parent.write(b, off, len);
        size += len;
    }

    @Override
    public void flush() throws IOException {
        parent.flush();
    }

    @Override
    public void close() throws IOException {
        parent.close();
    }

}
