package com.vlkan.rfos;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class RotationTest {

    @Test
    public void test() throws Exception {
        RotationConfig config = RotationConfig
                .builder()
                .file("/tmp/app.log")
                .filePattern("/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log")
                .policy(new SizeBasedRotationPolicy(0, 1024  /* 1 kb */))
                .callback(new RotationCallback() {

                    @Override
                    public void onTrigger(RotationPolicy policy, Instant instant) {
                        System.out.format("onTrigger(%s, %s)%n", policy, instant);
                    }

                    @Override
                    public void onSuccess(RotationPolicy policy, Instant instant, File file) {
                        System.out.format("onSuccess(%s, %s, %s)%n", policy, instant, file);

                    }

                    @Override
                    public void onFailure(RotationPolicy policy, Instant instant, File file, Exception error) {
                        System.out.format("onFailure(%s, %s, %s)%n", policy, instant, file);
                        error.printStackTrace();
                    }

                })
                .build();

        try (RotatingFileOutputStream fos = new RotatingFileOutputStream(config)) {
            byte[] bytes = ("Hello World!" +
                    "Hello World!" +
                    "Hello World!" +
                    "Hello World!" +
                    "Hello World!" +
                    "Hello World!" +
                    "Hello World!" +
                    "Hello World!").getBytes(StandardCharsets.UTF_8);
            for (int i=0; i<100; i++) {
                fos.write(bytes);
                Thread.sleep(10);
            }
        }
    }

}
