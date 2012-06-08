#!/bin/sh

JAVA=/usr/local/jdk1.7.0_04/bin/java
BC=./bcprov-jdk15on-147.jar
CP=:bin:$BC

"$JAVA" -cp $CP org.nick.abe.Main $*

