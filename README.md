Android backup extractor
========================

Utility to extract and repack Android backups created with adb logcat (ICS).
Largely based on BackupManagerService.java from AOSP.

Steps:

#
create abe.jar with mvn package



```java -jar abe.jar pack|unpack [parameters as below]```


```
   Android backup extractor
Usage:
	unpack:
		abe unpack <backup.ab> <backup.tar> [password]
	pack:
		abe pack <backup.tar> <backup.ab> [password]

	tips:
		in case of <backup.tar> is - stdin will be read from/written to stdout
		in case of <backup.ab>  is - stdin will be read from/written to stdout

		 cat x.tar | ab pack - - > x.ab
```


More details about the backup format and the tool implementation in the
associated blog post:

http://nelenkov.blogspot.com/2012/06/unpacking-android-backups.html

my changes:
- refactored for use as lib
- added tests
- made a maven project
- all operations  now available via pipes ( cat tar.tar | java -jar abe.jar pack - ab.ab pass )
