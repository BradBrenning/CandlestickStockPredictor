@echo off
set FX=%PATH_TO_FX%
set LIB_DIR=lib
set OUT_DIR=bin

java --module-path %FX% --add-modules javafx.controls,javafx.fxml ^
-classpath "%OUT_DIR%;%LIB_DIR%\mysql-connector-j-9.4.0.jar" services.App

pause
