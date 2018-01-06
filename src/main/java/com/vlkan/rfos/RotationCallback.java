package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.joda.time.LocalDateTime;

import java.io.File;

public interface RotationCallback {

    void onSuccess(RotationPolicy policy, LocalDateTime dateTime, File file);

    void onFailure(RotationPolicy policy, LocalDateTime dateTime, File file, Exception error);

}
