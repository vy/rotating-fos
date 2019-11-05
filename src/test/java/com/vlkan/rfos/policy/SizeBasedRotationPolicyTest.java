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

import com.vlkan.rfos.*;
import org.junit.Test;

import java.io.File;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SizeBasedRotationPolicyTest {

    @Test
    public void test_timer_triggered_rotation() throws InterruptedException {

        // Create a timer.
        @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
        BlockingQueue<Object> timerTaskExecutionPermits = new LinkedBlockingDeque<>();
        BlockingQueue<Long> timerDelays = new LinkedBlockingDeque<>(1);
        BlockingQueue<Long> timerPeriods = new LinkedBlockingDeque<>(1);
        Timer timer = new Timer() {
            @Override
            public void schedule(TimerTask task, long delay, long period) {
                new Thread(() -> {
                    boolean first = true;
                    while (true) {
                        try {
                            timerTaskExecutionPermits.poll(1, TimeUnit.SECONDS);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        if (first) {
                            first = false;
                            try {
                                timerDelays.put(delay);
                                timerPeriods.put(period);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        task.run();
                    }
                }).start();
            }
        };

        // Create the config.
        Clock clock = mock(Clock.class);
        File file = mock(File.class);
        RotatingFilePattern filePattern = mock(RotatingFilePattern.class);
        long checkIntervalMillis = 30_000L;
        long maxByteCount = 1024L * 1024L * 32L;    // 32MB
        SizeBasedRotationPolicy policy = new SizeBasedRotationPolicy(checkIntervalMillis, maxByteCount);
        RotationConfig config = RotationConfig
                .builder()
                .file(file)
                .filePattern(filePattern)
                .clock(clock)
                .timer(timer)
                .policy(policy)
                .build();

        // Create a rotatable.
        BlockingQueue<RotationPolicy> rotationPolicies = new LinkedBlockingDeque<>(1);
        BlockingQueue<String> rotationInstantTexts = new LinkedBlockingDeque<>(1);
        Rotatable rotatable = Rotatables.createSpyingRotatable(config, rotationPolicies, rotationInstantTexts);

        // Start policy. (Will consume the 1st clock tick.)
        policy.start(rotatable);

        // Setup the 1st clock tick.
        String now1Text = "2017-12-31T00:00:00.000Z";
        when(clock.now()).thenReturn(Instant.parse(now1Text));

        // Setup the 1st file length probe.
        when(file.length()).thenReturn(1024L);

        // Allow timer to proceed.
        timerTaskExecutionPermits.put(1L);

        // Consume the 1st blocking queue entries.
        Long timerDelay1 = timerDelays.poll(1, TimeUnit.SECONDS);
        assertThat(timerDelay1).isEqualTo(0L);
        Long timerPeriod1 = timerPeriods.poll(1, TimeUnit.SECONDS);
        assertThat(timerPeriod1).isEqualTo(checkIntervalMillis);
        RotationPolicy rotationPolicy1 = rotationPolicies.peek();
        assertThat(rotationPolicy1).isNull();
        String rotationInstantText1 = rotationInstantTexts.peek();
        assertThat(rotationInstantText1).isNull();

        // Setup the 2nd file length probe.
        when(file.length()).thenReturn(maxByteCount + 1);

        // Allow timer to proceed.
        timerTaskExecutionPermits.put(2L);

        // Consume the 2nd blocking queue entries.
        RotationPolicy rotationPolicy2 = rotationPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(rotationPolicy2).isEqualTo(policy);
        String rotationInstantText2 = rotationInstantTexts.poll(1, TimeUnit.SECONDS);
        assertThat(rotationInstantText2).isEqualTo(now1Text);

    }

}
