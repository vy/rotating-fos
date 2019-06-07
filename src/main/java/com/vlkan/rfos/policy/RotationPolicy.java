package com.vlkan.rfos.policy;

import com.vlkan.rfos.Rotatable;

public interface RotationPolicy {

    void start(Rotatable rotatable);

    boolean isWriteSensitive();

    void acceptWrite(long byteCount);

}
