package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import org.joda.time.LocalDateTime;

public interface Rotatable {

    void rotate(RotationPolicy policy, LocalDateTime dateTime);

    RotationConfig getConfig();

}
