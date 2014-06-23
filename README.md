Android backup extractor
========================

Utility to extract and repack Android backups created with ```adb backup``` (ICS+). 
Largely based on BackupManagerService.java from AOSP. 

Usage: 

Download the latest version of Bouncy Castle Provider jar 
(```bcprov-jdk15on-150.jar```) from here:

http://www.bouncycastle.org/latest_releases.html

Drop the latest Bouncy Castle jar in lib/, import in Eclipse and adjust 
build path if necessary. Use the abe.sh script to start the utility. 
Syntax: 

	unpack:	        abe.sh unpack  <backup.ab> <backup.tar> [password]
	pack:	        abe.sh pack    <backup.tar> <backup.ab> [password]
	pack for 4.4:	abe.sh pack-kk <backup.tar> <backup.ab> [password]
    (creates version 2 backups, compatible with Android 4.4.3)

If you don't specify a password the backup archive won't be encrypted but 
only compressed. 

Alternatively: 

Use the bundled Ant script to create an all-in-one jar and run with: 
(you still need to put the Bouncy Castle jar in lib/, and to edit the 
build.xml to change version number of the Bouncy Castle jar to the one
you downloaded. For example, if you downloaded bcprov-jdk15on-150.jar
and put it to the lib/ directory, you need to change 
"lib/bcprov-jdk15on-148.jar" in two lines in the build.xml to 
"lib/bcprov-jdk15on-150.jar" in order to match the library.)

```java -jar abe.jar pack|unpack [parameters as above]```

(Thanks to Jan Peter Stotz for contributing the build.xml file)

More details about the backup format and the tool implementation in the 
associated blog post: 

http://nelenkov.blogspot.com/2012/06/unpacking-android-backups.html

