@echo off
chcp 65001 >nul
if not exist out\br\com\paralelismo\Main.class (
    call compilar.bat
    if %errorlevel% neq 0 exit /b %errorlevel%
)
echo Executando aplicacao...
java --enable-native-access=ALL-UNNAMED -cp "out;lib\jocl-2.0.4.jar" br.com.paralelismo.Main %*
