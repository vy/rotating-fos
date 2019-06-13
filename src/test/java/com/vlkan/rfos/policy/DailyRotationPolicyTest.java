package com.vlkan.rfos.policy;

import com.vlkan.rfos.Clock;
import com.vlkan.rfos.Rotatable;
import com.vlkan.rfos.Rotatables;
import com.vlkan.rfos.RotationConfig;
import com.vlkan.rfos.RotatingFilePattern;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import java.io.File;
import java.util.Date;
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
        BlockingQueue<String> timerDateTimeTexts = new LinkedBlockingDeque<>();
        Timer timer = new Timer() {
            @Override
            public void schedule(TimerTask task, Date date) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String timerDateTimeText = LocalDateTime.fromDateFields(date).toString();
                        try {
                            timerDateTimeTexts.put(timerDateTimeText);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        task.run();
                    }
                }).start();
            }
        };

        // Setup the 1st clock tick.
        Clock clock = mock(Clock.class);
        String midnight1Text = "2017-12-29T00:00:00.000";
        when(clock.midnight()).thenReturn(LocalDateTime.parse(midnight1Text));

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
        BlockingQueue<String> rotationDateTimeTexts = new LinkedBlockingDeque<>(1);
        Rotatable rotatable = Rotatables.createSpyingRotatable(config, rotationPolicies, rotationDateTimeTexts);

        // Start policy. (Will consume the 1st clock tick.)
        policy.start(rotatable);

        // Setup the 2nd clock tick. (Will be consumed when we start draining from blocking queues.)
        String midnight2Text = "2017-12-30T00:00:00.000";
        when(clock.midnight()).thenReturn(LocalDateTime.parse(midnight2Text));

        // Consume the 1st blocking queue entries.
        String actualTimerDateTimeText1 = timerDateTimeTexts.poll(1, TimeUnit.SECONDS);
        assertThat(actualTimerDateTimeText1).isEqualTo(midnight1Text);
        RotationPolicy rotationPolicy1 = rotationPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(rotationPolicy1).isEqualTo(policy);
        String rotationDateTimeText1 = rotationDateTimeTexts.poll(1, TimeUnit.SECONDS);
        assertThat(rotationDateTimeText1).isEqualTo(midnight1Text);

        // Consume the 2nd blocking queue entries.
        String actualTimerDateTimeText2 = timerDateTimeTexts.poll(1, TimeUnit.SECONDS);
        assertThat(actualTimerDateTimeText2).isEqualTo(midnight2Text);
        RotationPolicy rotationPolicy2 = rotationPolicies.poll(1, TimeUnit.SECONDS);
        assertThat(rotationPolicy2).isEqualTo(policy);
        String rotationDateTimeText2 = rotationDateTimeTexts.poll(1, TimeUnit.SECONDS);
        assertThat(rotationDateTimeText2).isEqualTo(midnight2Text);

    }

}
