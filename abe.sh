#!/bin/sh

JAVA=/usr/local/jdk1.7.0_51/bin/java
BC=./lib/bcprov-jdk15on-150.jar
CP=:bin:$BC

"$JAVA" -cp $CP org.nick.abe.Main $*

