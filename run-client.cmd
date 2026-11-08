@echo off
REM =====================================================================
REM Funcao deste arquivo: compilar o projeto e iniciar um CLIENTE
REM Quoridor no Windows (equivalente ao run-client.sh).
REM
REM Uso: run-client.cmd [--console] [host] [porta]
REM   Sem argumentos, abre a interface grafica e pergunta nome, host
REM   e porta na tela de conexao. Execute este script 4 vezes (em 4
REM   terminais) para abrir os 4 clientes da partida.
REM
REM Fluxo: tenta compilar com o Maven; se o Maven nao estiver
REM instalado, compila direto com o javac; depois executa o cliente,
REM repassando todos os argumentos recebidos (%*).
REM =====================================================================
setlocal

REM Verifica se o Maven esta disponivel no PATH.
where mvn >nul 2>&1
if errorlevel 1 goto compile_javac

REM Caminho 1: compila e empacota com o Maven (sem clean, para ser rapido).
mvn -q package
if errorlevel 1 exit /b 1
goto run

:compile_javac
REM Caminho 2 (sem Maven): compila todos os .java com o javac.
if not exist target\classes mkdir target\classes
dir /s /b "src\main\java\*.java" > "%TEMP%\quoridor-sources.txt"
javac -encoding UTF-8 -d target\classes @"%TEMP%\quoridor-sources.txt"
if errorlevel 1 exit /b 1

:run
REM Inicia o cliente (grafico por padrao; --console para modo texto).
java -cp target\classes br.uesc.quoridor.client.ClientMain %*
