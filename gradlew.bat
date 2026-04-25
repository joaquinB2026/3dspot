@ECHO OFF
SETLOCAL

SET DIRNAME=%~dp0
SET APP_HOME=%DIRNAME%

SET DEFAULT_JVM_OPTS=

SET CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
