#!/bin/sh

# Gradle startup script for POSIX
app_path=$0

while [ -h "$app_path" ] ; do
    ls=`ls -ld "$app_path"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        app_path="$link"
    else
        app_path=`dirname "$app_path"`"/$link"
    fi
done

APP_BASE_NAME=`basename "$0"`
APP_HOME=`cd "\`dirname "$app_path"\`" >/dev/null 2>&1 && pwd`
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# If gradle-wrapper.jar is missing, try using system gradle first
if [ ! -f "$CLASSPATH" ]; then
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    else
        echo "gradle-wrapper.jar not found at $CLASSPATH and gradle not found in PATH."
        echo "Attempting to download gradle-wrapper.jar..."
        mkdir -p "$APP_HOME/gradle/wrapper"
        curl -sLo "$CLASSPATH" https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || \
        wget -qO "$CLASSPATH" https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || true
    fi
fi

if [ -f "$CLASSPATH" ]; then
    # Determine the Java command to use to start the JVM.
    if [ -n "$JAVA_HOME" ] ; then
        if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
            JAVACMD=$JAVA_HOME/jre/sh/java
        else
            JAVACMD=$JAVA_HOME/bin/java
        fi
    else
        JAVACMD=java
    fi

    DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
    eval set -- "$DEFAULT_JVM_OPTS" "$JAVA_OPTS" "$GRADLE_OPTS" "\"-Dorg.gradle.appname=$APP_BASE_NAME\"" -classpath "\"$CLASSPATH\"" org.gradle.wrapper.GradleWrapperMain "$@"
    exec "$JAVACMD" "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "ERROR: Neither gradle nor gradle-wrapper.jar could be executed."
    exit 1
fi
