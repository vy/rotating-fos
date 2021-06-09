/*
 * Copyright 2018-2020 Volkan Yazıcı
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

package com.vlkan.rfos;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import com.vlkan.rfos.policy.RotationPolicy;
import com.vlkan.rfos.policy.SizeBasedRotationPolicy;

/**
 * RotationConfig
 * 
 * rollingFile - rolling file by indexes, based on SizeBasedRotationPolicy, similar to log4j RollingFileAppender.
 */
public class RotationConfig {

    private enum DefaultExecutorServiceHolder {;

        private static final ScheduledExecutorService INSTANCE = createDefaultExecutorService();

        private static ScheduledThreadPoolExecutor createDefaultExecutorService() {
            int threadCount = readDefaultThreadCount();
            return new ScheduledThreadPoolExecutor(
                    threadCount,
                    new ThreadFactory() {

                        private int threadCount = 0;

                        @Override
                        public synchronized Thread newThread(Runnable runnable) {
                            String name = String.format("RotationJanitor-%02d", ++threadCount);
                            Thread thread = new Thread(runnable, name);
                            thread.setDaemon(true);
                            return thread;
                        }

                    });
        }

        private static int readDefaultThreadCount() {
            String threadCountProperty = System.getProperty("RotationJanitorCount");
            return threadCountProperty != null
                    ? Integer.parseInt(threadCountProperty)
                    : Runtime.getRuntime().availableProcessors();
        }

    }

    private final File file;

    private final RotatingFilePattern filePattern;

    private final ScheduledExecutorService executorService;

    private final Set<RotationPolicy> policies;

    private final boolean append;

    private final boolean compress;
    
    private final boolean rollingFile;
    
    private final Integer maxBackupIndex;

    private final Clock clock;

    private final RotationCallback callback;

    private RotationConfig(Builder builder) {
        this.file = builder.file;
        this.filePattern = builder.filePattern;
        this.executorService = builder.executorService;
        this.policies = builder.policies;
        this.append = builder.append;
        this.compress = builder.compress;
        this.rollingFile = builder.rollingFile;
        this.maxBackupIndex = builder.maxBackupIndex;
        this.clock = builder.clock;
        this.callback = builder.callback;
    }

    public File getFile() {
        return file;
    }

    public RotatingFilePattern getFilePattern() {
        return filePattern;
    }

    public static ScheduledExecutorService getDefaultExecutorService() {
        return DefaultExecutorServiceHolder.INSTANCE;
    }

    public ScheduledExecutorService getExecutorService() {
        return executorService;
    }

    public Set<RotationPolicy> getPolicies() {
        return policies;
    }

    public boolean isAppend() {
        return append;
    }

    public boolean isCompress() {
        return compress;
    }

    public boolean isRollingFile() {
		return rollingFile;
	}

	public Integer getMaxBackupIndex() {
		return maxBackupIndex;
	}

	public Clock getClock() {
        return clock;
    }

    public RotationCallback getCallback() {
        return callback;
    }

    @Override
    public boolean equals(Object instance) {
        if (this == instance) return true;
        if (instance == null || getClass() != instance.getClass()) return false;
        RotationConfig that = (RotationConfig) instance;
        return append == that.append &&
                compress == that.compress &&
        		rollingFile == that.rollingFile &&
				Objects.equals(maxBackupIndex, that.maxBackupIndex) &&
                Objects.equals(file, that.file) &&
                Objects.equals(filePattern, that.filePattern) &&
                Objects.equals(executorService, that.executorService) &&
                Objects.equals(policies, that.policies) &&
                Objects.equals(clock, that.clock) &&
                Objects.equals(callback, that.callback);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, filePattern, executorService, policies, append, compress, rollingFile, maxBackupIndex, clock, callback);
    }

    @Override
    public String toString() {
        return String.format("RotationConfig{file=%s}", file);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private File file;

        private RotatingFilePattern filePattern;

        private ScheduledExecutorService executorService;

        private Set<RotationPolicy> policies;

        private boolean append = true;

        private boolean compress = false;
        
        private boolean rollingFile = false;
        private Integer maxBackupIndex = null;

        private Clock clock = SystemClock.getInstance();

        private RotationCallback callback = LoggingRotationCallback.getInstance();

        private Builder() {
            // Do nothing.
        }

        public Builder file(File file) {
            this.file = file;
            return this;
        }

        public Builder file(String fileName) {
            this.file = new File(fileName);
            return this;
        }

        public Builder filePattern(RotatingFilePattern filePattern) {
            this.filePattern = filePattern;
            return this;
        }

        public Builder filePattern(String filePattern) {
            this.filePattern = RotatingFilePattern.builder().pattern(filePattern).build();
            return this;
        }

        public Builder executorService(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public Builder policies(Set<RotationPolicy> policies) {
            this.policies = policies;
            return this;
        }

        public Builder policy(RotationPolicy policy) {
            if (policies == null) {
                policies = new LinkedHashSet<>();
            }
            policies.add(policy);
            return this;
        }

        public Builder append(boolean append) {
            this.append = append;
            return this;
        }

        public Builder compress(boolean compress) {
            this.compress = compress;
            return this;
        }
        
        public Builder rollingFile(boolean rollingFile) {
            this.rollingFile = rollingFile;
            return this;
        }
        
        public Builder maxBackupIndex(int maxBackupIndex) {
            this.maxBackupIndex = maxBackupIndex;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder callback(RotationCallback callback) {
            this.callback = callback;
            return this;
        }

        public RotationConfig build() {
            prepare();
            validate();
            return new RotationConfig(this);
        }

        private void prepare() {
            if (executorService == null) {
                executorService = getDefaultExecutorService();
            }
        }

        private void validate() {
            Objects.requireNonNull(file, "file");
            if (rollingFile) {
            	boolean singleRotationPolicy = false;
            	if (policies != null && policies.size() == 1) {
            		RotationPolicy rotationPolicy = policies.iterator().next();
            		if (rotationPolicy instanceof SizeBasedRotationPolicy) {
            			singleRotationPolicy = true;
            		}
            	}
            	if (!singleRotationPolicy) {
            		throw new IllegalArgumentException("Expecting single SizeBasedRotationPolicy when using rollingFile.");
            	}
            	if (filePattern != null) {
            		throw new IllegalArgumentException("filePattern cannot be set when using rollingFile.");
            	}
            	Objects.requireNonNull(maxBackupIndex, "maxBackupIndex");
            	if (maxBackupIndex <= 0) {
            		throw new IllegalArgumentException("maxBackupIndex is out of range, must be positive.");
            	}
            } else {
	            Objects.requireNonNull(filePattern, "filePattern");
	            if (policies == null || policies.isEmpty()) {
	                throw new IllegalArgumentException("empty policies");
	            }
            }
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(callback, "callback");
        }

    }

}
