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

package com.vlkan.rfos;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.ThreadLocalRandom;

public class RotatingFileOutputStreamBenchmark {

    public static void main(String[] args) throws Exception {
        fixJavaClassPath();
        setLog4jConfig();
        ChainedOptionsBuilder optionsBuilder = new OptionsBuilder()
                .include(RotatingFileOutputStreamBenchmark.class.getSimpleName())
                .forks(2)
                .warmupIterations(3)
                .warmupTime(TimeValue.seconds(20))
                .measurementIterations(3)
                .measurementTime(TimeValue.seconds(30));
        configJmhQuickRun(optionsBuilder);
        configJmhJsonOutput(optionsBuilder);
        configJmhConcurrency(optionsBuilder);
        Options options = optionsBuilder.build();
        new Runner(options).run();
    }

    /**
     * Add project dependencies to <code>java.class.path</code> property used by JMH.
     *
     * @see <a href="https://stackoverflow.com/q/35574688/1278899">How to Run a JMH Benchmark in Maven Using exec:java Instead of exec:exec</a>
     */
    private static void fixJavaClassPath() {
        URLClassLoader classLoader = (URLClassLoader) RotatingFileOutputStreamBenchmark.class.getClassLoader();
        StringBuilder classpathBuilder = new StringBuilder();
        for (URL url : classLoader.getURLs()) {
            String urlPath = url.getPath();
            classpathBuilder.append(urlPath).append(File.pathSeparator);
        }
        String classpath = classpathBuilder.toString();
        System.setProperty("java.class.path", classpath);
    }

    private static void setLog4jConfig() {
        System.setProperty("log4j.configurationFile", "log4j2-quiet.xml");
    }

    private static void configJmhQuickRun(ChainedOptionsBuilder optionsBuilder) {
        String quick = System.getProperty("rfos.benchmark.quick");
        if (quick != null) {
            optionsBuilder
                    .forks(0)
                    .warmupIterations(0)
                    .measurementIterations(1)
                    .measurementTime(TimeValue.seconds(3));
        }
    }

    private static void configJmhJsonOutput(ChainedOptionsBuilder optionsBuilder) {
        String jsonOutputFile = System.getProperty("rfos.benchmark.jsonOutputFile");
        if (jsonOutputFile != null) {
            optionsBuilder
                    .resultFormat(ResultFormatType.JSON)
                    .result(jsonOutputFile);
        }
    }

    private static void configJmhConcurrency(ChainedOptionsBuilder optionsBuilder) {
        String concurrencyString = System.getProperty("rfos.benchmark.concurrency");
        if (concurrencyString != null) {
            int concurrency = Integer.parseInt(concurrencyString);
            optionsBuilder.threads(concurrency);
        }
    }

    @State(Scope.Thread)
    public static class Writer {

        private static final int BUFFER_SIZE = 16 * 1_024;

        private final byte[] buffer = new byte[BUFFER_SIZE];

        private int index = 0;

        public Writer() {
            ThreadLocalRandom.current().nextBytes(buffer);
        }

        private int writeByte(OutputStream outputStream) throws IOException {
            byte b = buffer[index];
            outputStream.write(b);
            return shiftIndex();
        }

        private int writeByteArray(OutputStream outputStream) throws IOException {
            outputStream.write(buffer, index, buffer.length - index);
            return shiftIndex();
        }

        private int shiftIndex() {
            return (index = ++index % BUFFER_SIZE);
        }

    }

    @State(Scope.Benchmark)
    public static class FosSource {

        private final OutputStream outputStream;

        public FosSource() {
            String path = System.getProperty("os.name").toLowerCase().contains("win")
                    ? "C:\\nul"     // https://stackoverflow.com/a/27773642/1278899
                    : "/dev/null";
            try {
                this.outputStream = new FileOutputStream(path);
            } catch (FileNotFoundException error) {
                throw new IllegalStateException(error);
            }
        }

    }

    @Benchmark
    public static int fos_1b(FosSource source, Writer writer) throws IOException {
        return writer.writeByte(source.outputStream);
    }

    @Benchmark
    public static int fos_ba(FosSource source, Writer writer) throws IOException {
        return writer.writeByteArray(source.outputStream);
    }

}
