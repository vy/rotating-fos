package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;

import java.util.TimerTask;

public abstract class TimeBasedRotationPolicy implements RotationPolicy {

    @Override
    public void start(RotationPolicyContext context) {
        LocalDateTime triggerDateTime = getTriggerDateTime(context.getClock());
        TimerTask timerTask = createTimerTask(context, triggerDateTime);
        context.getTimer().schedule(timerTask, triggerDateTime.toDate());
    }

    private TimerTask createTimerTask(final RotationPolicyContext context, final LocalDateTime triggerDateTime) {
        return new TimerTask() {
            @Override
            public void run() {
                getLogger().debug("triggering {triggerDateTime={}}", triggerDateTime);
                context.getRotatable().rotate(TimeBasedRotationPolicy.this, triggerDateTime, context.getCallback());
                start(context);
            }
        };
    }

    abstract public LocalDateTime getTriggerDateTime(Clock clock);

    abstract protected Logger getLogger();

}
