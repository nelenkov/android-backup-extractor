#!/bin/sh

JAVA=$JAVA_HOME/bin/java
BC=./lib/bcprov-jdk15on-150.jar
CP=:bin:$BC

"$JAVA" -cp $CP org.nick.abe.Main $*

