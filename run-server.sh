#!/usr/bin/env bash
# =====================================================================
# Função deste arquivo: compilar o projeto e iniciar o SERVIDOR
# Quoridor RMI no Linux/macOS (equivalente ao run-server.cmd).
#
# Uso: ./run-server.sh [hostname]
#   O argumento opcional é o hostname/IP que o RMI anuncia aos
#   clientes; sem ele, o servidor usa "localhost" (ver ServerMain).
# =====================================================================

# Encerra o script na primeira falha de qualquer comando.
set -e

# Compila e empacota o projeto com o Maven (do zero, com clean).
mvn clean package

# Inicia o servidor RMI (Registry na porta 1099, serviço "Quoridor").
java -cp target/classes br.uesc.quoridor.server.ServerMain "$1"
