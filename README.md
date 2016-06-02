Android backup extractor
========================

Utility to extract and repack Android backups created with ```adb backup``` (ICS+). 
Largely based on BackupManagerService.java from AOSP. 

Requires Java 7. Handling encrypted backups requires the JCE unlimited strength 
jurisdiction policy.

http://www.oracle.com/technetwork/java/javase/downloads/jce-7-download-432124.html

Usage (Eclipse): 

Download the latest version of Bouncy Castle Provider jar 
(```bcprov-jdk15on-*.jar```) from here:

http://www.bouncycastle.org/latest_releases.html

Drop the latest Bouncy Castle jar in lib/, import in Eclipse and adjust 
build path if necessary. Use the ```abe``` script to start the utility. 
Syntax: 

	unpack:       abe unpack  <backup.ab> <backup.tar> [password]
	pack:         abe pack    <backup.tar> <backup.ab> [password]
	pack for 4.4: abe pack-kk <backup.tar> <backup.ab> [password]
    (creates version 2 backups, compatible with Android 4.4.3)

If the filename is `-`, then data is read from standard input or written to
standard output.

If the password is not given on the command line, then the environment variable
`ABE_PASSWD` is tried. If you don't specify a password the backup archive won't
be encrypted but only compressed. 

Alternatively with Ant: 

Use the bundled Ant script to create an all-in-one jar and run with: 
(you still need to put the Bouncy Castle jar in lib/; modify the 
```bcprov.jar``` property accordingly)

```java -jar abe.jar pack|unpack|pack-kk [parameters as above]```

(Thanks to Jan Peter Stotz for contributing the build.xml file)

Alternatively with Gradle:

Use gradle to create an all-in-one jar:
```./gradlew``` and then:

```java -jar build/libs/abe-all.jar pack|unpack|pack-kk [parameters as above]```

# Notes

More details about the backup format and the tool implementation in the [associated blog post](https://nelenkov.blogspot.de/2012/06/unpacking-android-backups.html).

### Packing tar archives

- Android is **very** particular about the order of files in the tar archive. The format is [described here](https://android.googlesource.com/platform/frameworks/base/+/4a627c71ff53a4fca1f961f4b1dcc0461df18a06).
- Incompatible tar archives lead to errors or even system crashes.
- Apps with the `allowBackup` flag set to `false` are [not backed up nor restored](https://android.googlesource.com/platform/frameworks/base/+/a858cb075d0c87e2965d401656ff2d5bc16406da).
  - *(you can try restoring manually via `adb push` and `adb shell`)*
- Errors are only printed to logcat, look out for `BackupManagerService`.

The safest way to pack a tar archive is to get the list of files from the original backup.tar file:
```shell
tar tf backup.tar | grep -F "com.package.name" > package.list
```
And then use that list to build the tar file. In the extracted backup directory:
```shell
tar cf restore.tar -T package.list
```
You can now pack `restore.tar` and try `adb restore restore.ab`
