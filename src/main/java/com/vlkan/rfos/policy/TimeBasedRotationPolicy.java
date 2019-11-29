/*
 * Copyright 2019 Volkan Yazıcı
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

package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationConfig;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public abstract class TimeBasedRotationPolicy implements RotationPolicy {

    @Override
    public boolean isWriteSensitive() {
        return false;
    }

    @Override
    public void acceptWrite(long byteCount) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start(Rotatable rotatable) {
        RotationConfig config = rotatable.getConfig();
        Clock clock = config.getClock();
        Instant currentInstant = clock.now();
        Instant triggerInstant = getTriggerInstant(clock);
        long triggerDelayMillis = Duration.between(currentInstant, triggerInstant).toMillis();
        Runnable task = createTask(rotatable, triggerInstant);
        config.getExecutorService().schedule(task, triggerDelayMillis, TimeUnit.MILLISECONDS);
    }

    private Runnable createTask(Rotatable rotatable, Instant triggerInstant) {
        return () -> {
            getLogger().debug("triggering {triggerInstant={}}", triggerInstant);
            rotatable.rotate(TimeBasedRotationPolicy.this, triggerInstant);
            start(rotatable);
        };
    }

    abstract public Instant getTriggerInstant(Clock clock);

    abstract protected Logger getLogger();

}
