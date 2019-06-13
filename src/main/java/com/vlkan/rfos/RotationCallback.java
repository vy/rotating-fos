package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;

import java.io.File;
import java.time.Instant;

public interface RotationCallback {

    void onTrigger(RotationPolicy policy, Instant instant);

    void onSuccess(RotationPolicy policy, Instant instant, File file);

    void onFailure(RotationPolicy policy, Instant instant, File file, Exception error);

}
