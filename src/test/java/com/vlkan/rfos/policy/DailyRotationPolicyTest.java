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

public class DailyRotationPolicyTest {

    @Test
    public void test() throws InterruptedException {

        // Create a timer.
        BlockingQueue<Long> timerDelays = new LinkedBlockingDeque<>();
        Timer timer = new Timer() {
            @Override
            public void schedule(TimerTask task, long delayMillis) {
                new Thread(() -> {
                    try {
                        timerDelays.put(delayMillis);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    task.run();
                }).start();
            }
        };

        // Setup the 1st clock tick.
        Clock clock = mock(Clock.class);
        String midnight1Text = "2017-12-29T00:00:00.000Z";
        Instant midnight1Instant = Instant.parse(midnight1Text);
        when(clock.now()).thenReturn(midnight1Instant);
        when(clock.midnight()).thenReturn(midnight1Instant);

        // Create the config.
        DailyRotationPolicy policy = DailyRotationPolicy.getInstance();
        File file = mock(File.class);
        RotatingFilePattern filePattern = mock(RotatingFilePattern.class);
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

        // Setup the 2nd clock tick. (Will be consumed when we start draining from blocking queues.)
        String midnight2Text = "2017-12-30T00:00:00.000Z";
        Instant midnight2Instant = Instant.parse(midnight2Text);
        when(clock.now()).thenReturn(midnight2Instant);
        when(clock.midnight()).thenReturn(midnight2Instant);

        // Consume the 1st blocking queue entries.
        Long timerDelay1 = timerDelays.poll(1, TimeUnit.SECONDS);
        assertThat(timerDelay1).isEqualTo(0L);
        RotationPolicy rotationPolicy1 = rotationPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(rotationPolicy1).isEqualTo(policy);
        String rotationInstantText1 = rotationInstantTexts.poll(1, TimeUnit.SECONDS);
        assertThat(rotationInstantText1).isEqualTo(midnight1Text);

        // Consume the 2nd blocking queue entries.
        Long timerDelay2 = timerDelays.poll(1, TimeUnit.SECONDS);
        assertThat(timerDelay2).isEqualTo(0L);
        RotationPolicy rotationPolicy2 = rotationPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(rotationPolicy2).isEqualTo(policy);
        String rotationDateTimeText2 = rotationInstantTexts.poll(1, TimeUnit.SECONDS);
        assertThat(rotationDateTimeText2).isEqualTo(midnight2Text);

    }

}
