#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d '.tools/zulu8.96.0.205-ca-jdk8.0.504-macosx_aarch64/Contents/Home' ]; then
  export JAVA_HOME="$PWD/.tools/zulu8.96.0.205-ca-jdk8.0.504-macosx_aarch64/Contents/Home"
fi
if [ -x '.tools/apache-maven-3.9.11/bin/mvn' ]; then
  task_maven="$PWD/.tools/apache-maven-3.9.11/bin/mvn"
else
  task_maven=mvn
fi
"$task_maven" -B clean test
mkdir -p target/verification
"$JAVA_HOME/bin/javac" -cp 'target/classes:SweetHome3D/lib/*:SweetHome3D/libtest/AppleJavaExtensions.jar' -d target/verification verification/AdditionalChecks.java
"$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp 'target/verification:target/classes:SweetHome3D/lib/*:SweetHome3D/libtest/AppleJavaExtensions.jar' AdditionalChecks submissions/task4-all-levels.pdf
