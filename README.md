Android backup extractor
========================

Utility to extract and repack Android backups created with ```adb backup``` (ICS+). 
Largely based on BackupManagerService.java from AOSP. 

Usage (Eclipse): 

Download the latest version of Bouncy Castle Provider jar 
(```bcprov-jdk15on-152.jar```) from here:

http://www.bouncycastle.org/latest_releases.html

Drop the latest Bouncy Castle jar in lib/, import in Eclipse and adjust 
build path if necessary. Use the ```abe``` script to start the utility. 
Syntax: 

	unpack:       abe unpack  <backup.ab> <backup.tar> [password]
	pack:         abe pack    <backup.tar> <backup.ab> [password]
	pack for 4.4: abe pack-kk <backup.tar> <backup.ab> [password]
    (creates version 2 backups, compatible with Android 4.4.3)

If you don't specify a password the backup archive won't be encrypted but 
only compressed. 

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

More details about the backup format and the tool implementation in the 
associated blog post: 

http://nelenkov.blogspot.com/2012/06/unpacking-android-backups.html

