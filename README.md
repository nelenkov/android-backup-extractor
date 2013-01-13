Android backup extractor
========================

Utility to extract and repack Android backups created with adb logcat (ICS).
Largely based on BackupManagerService.java from AOSP.

Usage:

create abe.jar with mvn assembly:single

```java -jar abe.jar pack|unpack [parameters as above]```

More details about the backup format and the tool implementation in the
associated blog post:

http://nelenkov.blogspot.com/2012/06/unpacking-android-backups.html

my changes:
- refactored for use as lib
- added tests
- made a maven project

