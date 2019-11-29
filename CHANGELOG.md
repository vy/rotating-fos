### (????-??-??) v0.8

- Overhauled tests.

- Improved documentation.

- Added `RotationCallback#onOpen()` method. (#6)

- Removed timer-based invocation from `SizeBasedRotationPolicy`.

- Replaced `Timer` and `Thread` usage with a shared `ScheduledExecutorService`.

- Fixed license discrepancies. (#3)

### (2019-06-13) v0.7

- Upgraded to Java 8.

- Replaced Joda Time with Java Date/Time API.

- Added support for `Locale` and `ZoneId` in `RotatingFilePattern`.

- Added pre-`write()` (that is, write-sensitive) policy execution support.

- Switched from GPL v3 to Apache License v2.0.

- Upgrade dependency versions.
