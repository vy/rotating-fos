# Caveats

- **`append` is enabled for `RotatingFileOutputStream` by default**, whereas
  it is disabled (and hence truncates the file at start) for standard
  `FileOutputStream` by default.

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

- **When `append` is enabled, be cautious while using `onOpen` and `onClose`
  callbacks.** These callbacks might be employed to introduce headers and/or
  footers to certain type of files, e.g., [CSV](https://en.wikipedia.org/wiki/Comma-separated_values).
  Though one needs to avoid injecting the same header and/or footer multiple
  times when a file is re-opened for append. Note that this is not a problem
  for files opened/closed via rotation.