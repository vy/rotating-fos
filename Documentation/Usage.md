# Usage

You first need to include `rotating-fos` in your Maven/Gradle dependencies:

```xml
<dependency>
    <groupId>com.vlkan.rfos</groupId>
    <artifactId>rotating-fos</artifactId>
    <version>${rotating-fos.version}</version>
</dependency>
```

(Note that the Java 9 module name is `com.vlkan.rfos`.)

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
        .compress(true)
        .policy(DailyRotationPolicy.getInstance())
        .build();

try (RotatingFileOutputStream stream = new RotatingFileOutputStream(config)) {
    stream.write("Hello, world!".getBytes(StandardCharsets.UTF_8));
}
```

Using `maxBackupCount`, one can also introduce a rolling scheme where rotated
files will be named as `file.0`, `file.1`, `file.2`, ..., `file.N` in the order
from the newest to the oldest, `N` denoting the `maxBackupCount`:

```java
RotationConfig config = RotationConfig
        .builder()
        .file("/tmp/app.log")
        .maxBackupCount(10)         // Set `filePattern` to `file.%i` and keep
                                    // the most recent 10 files.
        .policy(new SizeBasedRotationPolicy(1024 * 1024 * 100 /* 100MiB */))
        .build();

try (RotatingFileOutputStream stream = new RotatingFileOutputStream(config)) {
    stream.write("Hello, world!".getBytes(StandardCharsets.UTF_8));
}
```

`RotationConfig.Builder` supports the following methods:

| Method(s) | Description |
| --------- | ----------- |
| `file(File)`<br/>`file(String)` | file accessed (e.g., `/tmp/app.log`) |
| `filePattern(RotatingFilePattern)`<br/>`filePattern(String)`| The pattern used to generate files for moving after rotation, e.g., `/tmp/app-%d{yyyyMMdd-HHmmss-SSS}.log`. This option cannot be combined with `maxBackupCount`. |
| `policy(RotationPolicy)`<br/>`policies(Set<RotationPolicy> policies)` | rotation policies |
| `maxBackupCount(int)` | If greater than zero, rotated files will be named as `file.0`, `file.1`, `file.2`, ..., `file.N` in the order from the newest to the oldest, where `N` denoting the `maxBackupCount`. `maxBackupCount` defaults to `-1`, that is, no rolling. This option cannot be combined with `filePattern` or `compress`. |
| `executorService(ScheduledExecutorService)` | scheduler for time-based policies and compression tasks |
| `append(boolean)` | append while opening the `file` (defaults to `true`) |
| `compress(boolean)` | Toggles GZIP compression after rotation and defaults to `false`. This option cannot be combined with `maxBackupCount`. |
| `clock(Clock)` | clock for retrieving date and time (defaults to `SystemClock`) |
| `callback(RotationCallback)`<br/>`callbacks(Set<RotationCallback>)` | rotation callbacks (defaults to `LoggingRotationCallback`) |

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
| `onClose(RotationPolicy, Instant, OutputStream)` | invoked on stream close and during rotation |
| `onSuccess(RotationPolicy, Instant, File)` | invoked after a successful rotation |
| `onFailure(RotationPolicy, Instant, File, Exception)` | invoked after a failed rotation attempt |