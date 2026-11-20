Android backup extractor
========================

[![Build and release](https://github.com/nelenkov/android-backup-extractor/actions/workflows/build.yml/badge.svg)](https://github.com/nelenkov/android-backup-extractor/actions/workflows/build.yml)

Utility to extract and repack Android backups created with `adb backup` (ICS+).
Largely based on `BackupManagerService.java` from AOSP.

# Building

Building requires JDK 17+ (via the bundled Gradle wrapper). The compiled JAR targets Java 8+ bytecode (`--release 8`).

To create a self-executable all-in-one JAR:

```shell
./gradlew shadowJar
```

Then run:

```shell
java -jar build/libs/abe-all.jar pack|unpack|pack-kk ...
```

Or use the included `./abe` wrapper script, which locates `build/libs/abe-all.jar` automatically.

## Running tests

```shell
./gradlew test
```

See [Releases](#releases) for pre-built runnable JAR files.

# Usage

## Syntax
* unpack:       `abe unpack  <backup.ab> <backup.tar> [password]`
* pack:         `abe pack    <backup.tar> <backup.ab> [password]`
* pack for 4.4: `abe pack-kk <backup.tar> <backup.ab> [password]`
  (creates version 2 backups, compatible with Android 4.4.3)

If the filename is `-`, data is read from standard input or written to standard output.

If the password is not given on the command line, the environment variable `ABE_PASSWD` is tried. If no password is specified, the backup archive will not be encrypted, only compressed.

## Packing tar archives

- Android is **very** particular about the order of files in the tar archive. The format is [described here](https://android.googlesource.com/platform/frameworks/base/+/4a627c71ff53a4fca1f961f4b1dcc0461df18a06).
- Incompatible tar archives lead to errors or even system crashes.
- Apps with the `allowBackup` flag set to `false` are [not backed up nor restored](https://android.googlesource.com/platform/frameworks/base/+/a858cb075d0c87e2965d401656ff2d5bc16406da).
  - *(you can try restoring manually via `adb push` and `adb shell`)*
- Errors are only printed to logcat, look out for `BackupManagerService`.

The safest way to pack a tar archive is to get the list of files from the original `backup.tar` file:
```shell
tar tf backup.tar | grep -F "com.package.name" > package.list
```
And then use that list to build the tar file. In the extracted backup directory:
```shell
tar cf restore.tar -T package.list
```
You can now pack `restore.tar` and try `adb restore restore.ab`.

# Releases

[Releases](https://github.com/nelenkov/android-backup-extractor/releases/latest) are built with GitHub Actions when a version tag (`v*`, e.g. `v1.0.0`) is pushed and include a runnable fat JAR, its SHA-256 checksum, and build provenance attestation.

Use the binaries at your own risk. No warranty or support provided.

Please report only bugs in backup extractor itself; I can't answer questions regarding unpacking/repacking backups or tar files.
(See [Usage](#usage) for a mini usage guide.)

# Notes

More details about the backup format and the tool implementation in the [associated blog post](https://nelenkov.blogspot.de/2012/06/unpacking-android-backups.html).
