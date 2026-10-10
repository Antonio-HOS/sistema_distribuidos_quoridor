#!/usr/bin/env bash
set -e
mvn -q package
java -cp target/classes br.uesc.quoridor.client.ClientMain "$@"
