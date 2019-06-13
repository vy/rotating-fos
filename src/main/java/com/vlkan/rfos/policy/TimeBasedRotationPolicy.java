package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationConfig;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.TimerTask;

public abstract class TimeBasedRotationPolicy implements RotationPolicy {

    @Override
    public boolean isWriteSensitive() {
        return false;
    }

    @Override
    public void acceptWrite(long byteCount) {}

    @Override
    public void start(Rotatable rotatable) {
        RotationConfig config = rotatable.getConfig();
        Clock clock = config.getClock();
        Instant currentInstant = clock.now();
        Instant triggerInstant = getTriggerInstant(clock);
        long triggerDelayMillis = Duration.between(currentInstant, triggerInstant).toMillis();
        TimerTask timerTask = createTimerTask(rotatable, triggerInstant);
        config.getTimer().schedule(timerTask, triggerDelayMillis);
    }

    private TimerTask createTimerTask(Rotatable rotatable, Instant triggerInstant) {
        RotationConfig config = rotatable.getConfig();
        return new TimerTask() {
            @Override
            public void run() {
                getLogger().debug("triggering {triggerInstant={}}", triggerInstant);
                config.getCallback().onTrigger(TimeBasedRotationPolicy.this, triggerInstant);
                rotatable.rotate(TimeBasedRotationPolicy.this, triggerInstant);
                start(rotatable);
            }
        };
    }

    abstract public Instant getTriggerInstant(Clock clock);

    abstract protected Logger getLogger();

}
