@echo off
set OUT_DIR=bin
set SRC_DIR=src
set LIB_DIR=lib
set FX=%PATH_TO_FX%

rmdir /s /q %OUT_DIR% 2>nul
mkdir %OUT_DIR%

echo Compiling project...
javac --module-path %FX% --add-modules javafx.controls,javafx.fxml ^
-classpath "%LIB_DIR%\mysql-connector-j-9.4.0.jar" ^
-d %OUT_DIR% ^
%SRC_DIR%\views\*.java ^
%SRC_DIR%\models\*.java ^
%SRC_DIR%\controllers\*.java ^
%SRC_DIR%\services\*.java ^
%SRC_DIR%\coinbaseAPI\*.java

pause
