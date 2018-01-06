[![Build Status](https://secure.travis-ci.org/vy/rotating-fos.svg)](http://travis-ci.org/vy/rotating-fos)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.rfos/rotating-fos.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.rfos%22)

`rotating-fos` is a Java library providing `RotatingFileOutputStream` which
internally rotates an underlying `FileOutputStream` using provided rotation
policies similar to [Log4j](https://logging.apache.org/log4j/) or
[Logback](https://logback.qos.ch/).

# Usage

`RotatingFileOutputStream` does not extend `java.io.FileOutputStream` (as a
deliberate design decision, see [How (not) to extend standard collection
classes](https://javachannel.org/posts/how-not-to-extend-standard-collection-classes/)),
but `java.io.OutputStream`. Its basic usage is pretty straightforward:

```java
RotatingFileOutputStream
        .builder()
        .file("/tmp/app.log")
        .filePattern("/tmp/app-%{yyyyMMdd-HHmmss.SSS}.log")
        .policy(new SizeBasedRotationPolicy(1000, 1024 * 1024 * 100))
        .policy(DailyRotationPolicy.getInstance())
        .build();
```

`RotatingFileOutputStream.Builder` supports the following methods:

| Method(s) | Default | Description |
| --------- | ------- | ----------- |
| `file(File)`<br/>`file(String)` | N/A | file accessed (e.g., `/tmp/app.log`) |
| `filePattern(RotatingFilePattern)`<br/>`filePattern(String)`| N/A | rotated file pattern (e.g., `/tmp/app-%{yyyyMMdd-HHmmss-SSS}.log`) |
| `policy(RotationPolicy)`<br/>`policies(Set<RotationPolicy> policies)` | N/A | rotation policies |
| `timer(Timer)` | `new Timer()` | timer to be used for scheduling policies |
| `append(boolean)` | `true` | append while opening the `file` |
| `compress(boolean)` | `false` | GZIP compression after rotation |
| `clock(Clock)` | `SystemClock.getInstance()` | clock to be used for retrieving date and time |
| `callback(RotationCallback)` | `LoggingRotationCallback.getInstance()` | rotation callback |

Packaged rotation policies are listed below:

- `DailyRotationPolicy`
- `WeeklyRotationPolicy`
- `SizeBasedRotationPolicy`

You can also create your own rotation policies by implementing
`RotationPolicy` interface.

# Caveats

- **Rotated file conflicts are not resolved by `rotating-fos`!** Once a
  rotation policy gets triggered, `rotating-fos` applies the given
  `filePattern` to determine the rotated file name. In order to avoid
  previously generated files to be overridden, prefer a sufficiently
  fine-grained date-time pattern.

  For instance, given `filePattern` is `/tmp/app-%{yyyyMMdd}.log`, if
  `SizeBasedRotationPolicy` gets triggered multiple times within a day,
  the last one will override the earlier generations in the same day.
  In order to avoid this, you should have been using a date-time pattern
  with a higher resolution, such as `/tmp/app-%{yyyyMMdd-HHmmss-SSS}.log`.

# License

Copyright &copy; 2017 [Volkan Yazıcı](http://vlkan.com/)

rotating-fos is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.

rotating-fos is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
