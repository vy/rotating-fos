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

    OutputStream parent() {
        return parent;
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

}
