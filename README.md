[![Build Status](https://img.shields.io/endpoint.svg?url=https%3A%2F%2Factions-badge.atrox.dev%2Fvy%2Frotating-fos%2Fbadge%3Fref%3Dmaster&style=flat)](https://actions-badge.atrox.dev/vy/rotating-fos/goto?ref=master)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22)
[![License](https://img.shields.io/github/license/vy/rotating-fos.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)

`rotating-fos` is a Java 8 library providing `RotatingFileOutputStream` which
internally rotates a delegate `FileOutputStream` using provided rotation
policies similar to [logrotate](https://github.com/logrotate/logrotate),
[Log4j](https://logging.apache.org/log4j/) and [Logback](https://logback.qos.ch/).

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
        .policy(new SizeBasedRotationPolicy(1024 * 1024 * 100 /* 100MiB */))
        .policy(DailyRotationPolicy.getInstance())
        .build();

try (RotatingFileOutputStream stream = new RotatingFileOutputStream(config)) {
    stream.write("Hello, world!".getBytes(StandardCharsets.UTF_8));
}
```

`RotationConfig.Builder` supports the following methods:

| Method(s) | Description |
| --------- | ----------- |
| `file(File)`<br/>`file(String)` | file accessed (e.g., `/tmp/app.log`) |
| `filePattern(RotatingFilePattern)`<br/>`filePattern(String)`| rotating file pattern (e.g., `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`) |
| `policy(RotationPolicy)`<br/>`policies(Set<RotationPolicy> policies)` | rotation policies |
| `executorService(ScheduledExecutorService)` | scheduler for time-based policies and compression tasks |
| `append(boolean)` | append while opening the `file` (defaults to `true`) |
| `compress(boolean)` | GZIP compression after rotation (defaults to `false`) |
| `clock(Clock)` | clock for retrieving date and time (defaults to `SystemClock`) |
| `callback(RotationCallback)` | rotation callback (defaults to `LoggingRotationCallback`) |

The default `ScheduledExecutorService` can be retrieved via
`RotationConfig#getDefaultExecutorService()`, which is a
`ScheduledThreadPoolExecutor` of size `Runtime.getRuntime().availableProcessors()`.
Note that unless explicitly specified in `RotationConfig.Builder`, all instances
of `RotationConfig` (and hence of `RotatingFileOutputStream`) will share the
same `ScheduledExecutorService`. You can change the default pool size via
`RotationJanitorCount` system property.

Packaged rotation policies are listed below. (You can also create your own
rotation policies by implementing `RotationPolicy` interface.)

- `DailyRotationPolicy`
- `WeeklyRotationPolicy`
- `SizeBasedRotationPolicy`

Once you have a handle on `RotatingFileOutputStream`, in addition to standard
`java.io.OutputStream` methods (e.g., `write()`, `close()`, etc.), it provides
the following methods:

| Method | Description |
| ------ | ------------|
| `getConfig()` | employed `RotationConfig` |
| `rotate(RotationPolicy, Instant)` | trigger a rotation |

`RotatingFilePattern.Builder` supports the following methods:

| Method | Description |
| ------ | ----------- |
| `pattern(String)` | rotating file pattern (e.g., `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`) |
| `locale(Locale)` | `Locale` used in the `DateTimeFormatter` (defaults to `Locale.getDefault()`) |
| `timeZoneId(ZoneId)` | `ZoneId` denoting the time zone used in the `DateTimeFormatter` (defaults to `TimeZone.getDefault().toZoneId()`) |

Rotation-triggered custom behaviours can be introduced via `RotationCallback`
passed to `RotationConfig.Builder`. `RotationCallback` provides the following
methods.

| Method | Description |
| ------ | ----------- |
| `onTrigger(RotationPolicy, Instant)` | invoked at the beginning of every rotation attempt |
| `onOpen(RotationPolicy, Instant, OutputStream)` | invoked at start and during rotation |
| `onSuccess(RotationPolicy, Instant, File)` | invoked after a successful rotation |
| `onFailure(RotationPolicy, Instant, File, Exception)` | invoked after a failed rotation attempt |

# Caveats

- **Rotated file conflicts are not resolved by `rotating-fos`.** Once a
  rotation policy gets triggered, `rotating-fos` applies the given
  `filePattern` to determine the rotated file name. In order to avoid
  previously generated files to be overridden, prefer a sufficiently
  fine-grained date-time pattern.

  For instance, given `filePattern` is `/tmp/app-%d{yyyyMMdd}.log`, if
  `SizeBasedRotationPolicy` gets triggered multiple times within a day, the last
  one will override the earlier generations in the same day. In order to avoid
  this, you should use a date-time pattern with a higher resolution, such as
  `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`.

- **Make sure `RotationCallback` methods are not blocking.** Callbacks are
  invoked using the `ScheduledExecutorService` passed via `RotationConfig`.
  Hence blocking callback methods have a direct impact on time-sensitive
  policies and compression tasks.

# Contributors

- [Christoph (pitschr) Pitschmann](https://github.com/pitschr) (Windows-specific
  fixes, `RotationCallback#onOpen()` method)
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
