#!/bin/sh
java -cp -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005  bin/:src/Utilities/java_cup_runtime.jar Espressoc -I Include/ -Tsymbol -P:3 $@
