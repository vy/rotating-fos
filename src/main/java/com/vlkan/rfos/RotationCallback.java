package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;

import java.io.File;
import java.time.Instant;

public interface RotationCallback {

    void onTrigger(RotationPolicy policy, Instant dateTime);

    void onSuccess(RotationPolicy policy, Instant dateTime, File file);

    void onFailure(RotationPolicy policy, Instant dateTime, File file, Exception error);

}
