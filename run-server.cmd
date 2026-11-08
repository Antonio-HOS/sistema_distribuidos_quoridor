@echo off
REM =====================================================================
REM Funcao deste arquivo: compilar o projeto e iniciar o SERVIDOR
REM Quoridor RMI no Windows (equivalente ao run-server.sh).
REM
REM Uso: run-server.cmd [hostname]
REM   O argumento opcional e o hostname/IP que o RMI anuncia aos
REM   clientes; sem ele, o servidor usa "localhost" (ver ServerMain).
REM
REM Fluxo: tenta compilar com o Maven; se o Maven nao estiver
REM instalado, compila direto com o javac; depois executa o servidor.
REM =====================================================================
setlocal

REM Verifica se o Maven esta disponivel no PATH.
where mvn >nul 2>&1
if errorlevel 1 goto compile_javac

REM Caminho 1: compila e empacota com o Maven (do zero, com clean).
mvn clean package
if errorlevel 1 exit /b 1
goto run

:compile_javac
REM Caminho 2 (sem Maven): compila todos os .java com o javac.
if not exist target\classes mkdir target\classes
dir /s /b "src\main\java\*.java" > "%TEMP%\quoridor-sources.txt"
javac -encoding UTF-8 -d target\classes @"%TEMP%\quoridor-sources.txt"
if errorlevel 1 exit /b 1

:run
REM Inicia o servidor RMI (Registry na porta 1099, servico "Quoridor").
java -cp target\classes br.uesc.quoridor.server.ServerMain %1
