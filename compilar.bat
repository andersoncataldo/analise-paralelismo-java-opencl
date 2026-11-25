@echo off
chcp 65001 >nul
if not exist out mkdir out
echo Compilando projeto Java com JOCL 2.0.4...
javac -encoding UTF-8 -cp "lib\jocl-2.0.4.jar" -d out ^
  src\br\com\paralelismo\model\*.java ^
  src\br\com\paralelismo\algorithms\*.java ^
  src\br\com\paralelismo\benchmark\*.java ^
  src\br\com\paralelismo\ui\*.java ^
  src\br\com\paralelismo\Main.java
if %errorlevel% neq 0 (
    echo [ERRO] Falha na compilacao.
    exit /b %errorlevel%
)
echo Compilacao concluida com sucesso em .\out\
