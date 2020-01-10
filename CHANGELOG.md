### (2020-01-10) v0.9.2

- Shutdown the default `ScheduledExecutorService` at JVM exit. (#12)

### (2019-12-08) v0.9.1

- Added explicit Java 9 module name. (#11)

### (2019-12-03) v0.9.0

- Switched to semantic versioning scheme. (#10)

- Overhauled tests.

- Improved documentation.

- Added `onOpen()` (#6) and `onClose()` (#9) methods to `RotationCallback`.

- Removed timer-based invocation from `SizeBasedRotationPolicy`.

- Replaced `Timer` and `Thread` usage with a shared `ScheduledExecutorService`.

- Fixed license discrepancies. (#3)

### (2019-11-05) v0.8

- Add Windows build to CI pipeline. (#4)

- Switch from Travis CI to GitHub Actions. (#4)

- Fix stream handling for Windows. (#4)

### (2019-06-13) v0.7

- Upgraded to Java 8.

- Replaced Joda Time with Java Date/Time API.

- Added support for `Locale` and `ZoneId` in `RotatingFilePattern`.

- Added pre-`write()` (that is, write-sensitive) policy execution support.

- Switched from GPL v3 to Apache License v2.0.

- Upgrade dependency versions.
