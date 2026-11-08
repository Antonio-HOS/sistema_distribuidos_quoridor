/*
 * Função deste arquivo: definir o tipo GameState, que é a fotografia
 * completa e serializável de uma partida de Quoridor em um dado instante.
 *
 * É o objeto retornado por praticamente todas as chamadas remotas de
 * GameService: o servidor monta um GameState novo a cada consulta ou
 * jogada, ele viaja pela rede via RMI e o cliente apenas o exibe,
 * sem manter nenhuma regra de jogo localmente.
 */
package br.uesc.quoridor.common;

import java.io.Serializable;
import java.util.List;

/**
 * Estado completo de uma partida em um determinado instante.
 *
 * @param size          tamanho do tabuleiro (número de casas por lado, sempre 9)
 * @param players       lista com o estado de cada jogador já conectado
 * @param walls         lista de paredes já colocadas no tabuleiro
 * @param currentPlayer ID (0..3) do jogador da vez
 * @param started       {@code true} quando os 4 jogadores já entraram e a partida começou
 * @param winner        ID do vencedor, ou {@code null} enquanto ninguém venceu
 * @param message       mensagem textual do servidor descrevendo o último evento
 *                      (entrada de jogador, jogada, vitória, desconexão etc.)
 * @param legalMoves    casas para as quais o jogador da vez pode mover o peão;
 *                      lista vazia enquanto a partida não está em andamento
 * @param abandoned     {@code true} se a partida foi encerrada porque um jogador saiu
 */
public record GameState(
        int size,
        List<PlayerState> players,
        List<Wall> walls,
        int currentPlayer,
        boolean started,
        Integer winner,
        String message,
        List<Cell> legalMoves,
        boolean abandoned
) implements Serializable {
    // Record: o compilador gera automaticamente os métodos de acesso
    // size(), players(), walls(), currentPlayer(), started(), winner(),
    // message(), legalMoves() e abandoned(), além de equals(),
    // hashCode() e toString().
}
