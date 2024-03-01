/*
 * Copyright 2018-2024 Volkan Yazıcı <volkan@yazi.ci>
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
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Instant.EPOCH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimeBasedRotationPolicyTest {

    @Test
    void quick_task_scheduling_should_not_cause_repeated_rotations() {

        // Mock a system where everything happens very fast, that is, at the very same time!
        // This is a contrived assumption, but probable.
        // This can happen due to:
        // 1. Code execution is faster than the time resolution provided by the clock
        // 2. Clocks can return a value twice (due to daylight time savings, monotonically-increasing design, etc.)
        Clock clock = mock(Clock.class);
        when(clock.now()).thenReturn(EPOCH);

        // Create an executor that runs *the first two* tasks immediately (i.e., without any delay!) and discards the rest.
        // Why the first two?
        // 1. The first time `start()` is run manually (by `RotatingFileOutputStream`), it will schedule a task.
        // 2. When `start()` is run by the scheduled task, it will schedule a task, again.
        // 3. We need to stop here, otherwise we will be looping around step #2.
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        when(executor.schedule(any(Runnable.class), anyLong(), any()))
                .thenAnswer(new Answer<ScheduledFuture<?>>() {

                    private final AtomicInteger invocationCounter = new AtomicInteger(0);

                    @Override
                    public ScheduledFuture<?> answer(InvocationOnMock invocation) {
                        if (invocationCounter.getAndIncrement() < 2) {
                            Runnable task = invocation.getArgument(0);
                            task.run();
                        }
                        return null;
                    }

                });

        // Create the rotation configuration
        RotationConfig config = mock(RotationConfig.class);
        when(config.getClock()).thenReturn(clock);
        when(config.getExecutorService()).thenReturn(executor);

        // Create the rotatable
        Rotatable rotatable = mock(Rotatable.class);
        when(rotatable.getConfig()).thenReturn(config);

        // Create and start the policy
        PerNanoRotationPolicy policy = new PerNanoRotationPolicy();
        policy.start(rotatable);

        // Verify there was only a single rotation
        verify(rotatable, times(1)).rotate(any(), any());

    }

    private static class PerNanoRotationPolicy extends TimeBasedRotationPolicy {

        private static final Logger LOGGER = mock(Logger.class);

        @Override
        public Instant getTriggerInstant(Clock clock) {
            // Choose a sub-millisecond delay
            return clock.now().plusNanos(1);
        }

        @Override
        protected Logger getLogger() {
            return LOGGER;
        }

    }

}
