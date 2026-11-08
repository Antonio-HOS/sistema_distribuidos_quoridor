#!/usr/bin/env bash
# =====================================================================
# Função deste arquivo: compilar o projeto e iniciar um CLIENTE
# Quoridor no Linux/macOS (equivalente ao run-client.cmd).
#
# Uso: ./run-client.sh [--console] [host] [porta]
#   Sem argumentos, abre a interface gráfica e pergunta nome, host
#   e porta na tela de conexão. Execute este script 4 vezes (em 4
#   terminais) para abrir os 4 clientes da partida.
# =====================================================================

# Encerra o script na primeira falha de qualquer comando.
set -e

# Compila e empacota o projeto com o Maven (modo silencioso).
mvn -q package

# Inicia o cliente (gráfico por padrão; --console para modo texto),
# repassando todos os argumentos recebidos.
java -cp target/classes br.uesc.quoridor.client.ClientMain "$@"
