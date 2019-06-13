package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.RotationConfig;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;

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
        LocalDateTime triggerDateTime = getTriggerDateTime(config.getClock());
        TimerTask timerTask = createTimerTask(rotatable, triggerDateTime);
        config.getTimer().schedule(timerTask, triggerDateTime.toDate());
    }

    private TimerTask createTimerTask(Rotatable rotatable, LocalDateTime triggerDateTime) {
        RotationConfig config = rotatable.getConfig();
        return new TimerTask() {
            @Override
            public void run() {
                getLogger().debug("triggering {triggerDateTime={}}", triggerDateTime);
                config.getCallback().onTrigger(TimeBasedRotationPolicy.this, triggerDateTime);
                rotatable.rotate(TimeBasedRotationPolicy.this, triggerDateTime);
                start(rotatable);
            }
        };
    }

    abstract public LocalDateTime getTriggerDateTime(Clock clock);

    abstract protected Logger getLogger();

}
