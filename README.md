[![Build Status](https://travis-ci.org/vy/rotating-fos.svg)](https://travis-ci.org/vy/rotating-fos)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22)
[![License](https://img.shields.io/github/license/vy/log4j2-logstash-layout.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)

`rotating-fos` is a Java 8 library providing `RotatingFileOutputStream` which
internally rotates an underlying `FileOutputStream` using provided rotation
policies similar to [Log4j](https://logging.apache.org/log4j/) or
[Logback](https://logback.qos.ch/).

# Usage

You first need to include `rotating-fos` in your Maven/Gradle dependencies:

```xml
<dependency>
    <groupId>com.vlkan.rfos</groupId>
    <artifactId>rotating-fos</artifactId>
    <version>${rotating-fos.version}</version>
</dependency>
```

`RotatingFileOutputStream` does not extend `java.io.FileOutputStream` (as a
deliberate design decision, see [How (not) to extend standard collection
classes](https://javachannel.org/posts/how-not-to-extend-standard-collection-classes/)),
but `java.io.OutputStream`. Its basic usage is pretty straightforward:

```java
RotationConfig config = RotationConfig
        .builder()
        .file("/tmp/app.log")
        .filePattern("/tmp/app-%d{yyyyMMdd-HHmmss.SSS}.log")
        .policy(new SizeBasedRotationPolicy(5000 /* 5s */, 1024 * 1024 * 100 /* 100MB */))
        .policy(DailyRotationPolicy.getInstance())
        .build();

try (RotatingFileOutputStream stream = new RotatingFileOutputStream(config)) {
    stream.writer("Hello, world!".getBytes(StandardCharsets.UTF_8))
}
```

`RotationConfig.Builder` supports the following methods:

| Method(s) | Default | Description |
| --------- | ------- | ----------- |
| `file(File)`<br/>`file(String)` | N/A | file accessed (e.g., `/tmp/app.log`) |
| `filePattern(RotatingFilePattern)`<br/>`filePattern(String)`| N/A | rotating file pattern (e.g., `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`) |
| `policy(RotationPolicy)`<br/>`policies(Set<RotationPolicy> policies)` | N/A | rotation policies |
| `timer(Timer)` | `Timer` | timer for scheduling policies |
| `append(boolean)` | `true` | append while opening the `file` |
| `compress(boolean)` | `false` | GZIP compression after rotation |
| `clock(Clock)` | `SystemClock` | clock for retrieving date and time |
| `callback(RotationCallback)` | `LoggingRotationCallback` | rotation callback |

Packaged rotation policies are listed below. (You can also create your own
rotation policies by implementing `RotationPolicy` interface.)

- `DailyRotationPolicy`
- `WeeklyRotationPolicy`
- `SizeBasedRotationPolicy`

Once you have a handle on `RotatingFileOutputStream`, in addition to standard
`java.io.OutputStream` methods (e.g., `write()`, `close()`, etc.), it provides
the following methods:

| Method | Description |
| --------- | ----------- |
| `RotationConfig getConfig()` | used configuration |
| `List<Thread> getRunningThreads()` | compression threads running in the background |

`RotatingFilePattern.Builder` supports the following methods:

| Method | Default | Description |
| ------ | ------- | ----------- |
| `pattern(String)` | N/A | rotating file pattern (e.g., `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`) |
| `locale(Locale)` | `Locale.getDefault()` | `Locale` used in the `DateTimeFormatter` |
| `timeZoneId(ZoneId)` | `TimeZone.getDefault().toZoneId()` | `ZoneId` denoting the time zone used in the `DateTimeFormatter` |

# Caveats

- **Rotated file conflicts are not resolved by `rotating-fos`.** Once a
  rotation policy gets triggered, `rotating-fos` applies the given
  `filePattern` to determine the rotated file name. In order to avoid
  previously generated files to be overridden, prefer a sufficiently
  fine-grained date-time pattern.

  For instance, given `filePattern` is `/tmp/app-%d{yyyyMMdd}.log`, if
  `SizeBasedRotationPolicy` gets triggered multiple times within a day,
  the last one will override the earlier generations in the same day.
  In order to avoid this, you should have been using a date-time pattern
  with a higher resolution, such as `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`.

- **Make sure `RotationCallback` methods are not blocking.** Callbacks are
  invoked using the `Timer` thread passed via `RotationConfig`. Hence
  blocking callback methods are going to block `Timer` thread too.

# Contributors

- [Jonas (yawkat) Konrad](https://yawk.at/) (`RotatingFileOutputStream`
  thread-safety improvements)
- [Lukas Bradley](https://github.com/lukasbradley/)

# License

Copyright &copy; 2017-2019 [Volkan Yazıcı](https://vlkan.com/)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
