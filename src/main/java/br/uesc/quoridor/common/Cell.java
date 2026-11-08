/*
 * Função deste arquivo: definir o tipo Cell, que representa uma casa
 * (posição) do tabuleiro 9x9 do Quoridor.
 *
 * É um record serializável porque as instâncias de Cell trafegam pela
 * rede via Java RMI, dentro do GameState (por exemplo, na lista de
 * movimentos legais do jogador da vez).
 */
package br.uesc.quoridor.common;

import java.io.Serializable;

/**
 * Casa do tabuleiro identificada por linha e coluna.
 *
 * @param row linha da casa, no intervalo 0..8 (0 é o topo do tabuleiro)
 * @param col coluna da casa, no intervalo 0..8 (0 é a esquerda do tabuleiro)
 */
public record Cell(int row, int col) implements Serializable {
    // Record: o compilador gera automaticamente os métodos de acesso
    // row() e col(), além de equals(), hashCode() e toString().
}
