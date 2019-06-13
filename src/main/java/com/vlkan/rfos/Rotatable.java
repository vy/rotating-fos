package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;

import java.time.Instant;

public interface Rotatable {

    void rotate(RotationPolicy policy, Instant instant);

    RotationConfig getConfig();

}
