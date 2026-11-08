/*
 * Função deste arquivo: definir o tipo PlayerState, que descreve a
 * situação de um único jogador dentro da partida (quem é, onde está
 * seu peão e quantas paredes ainda lhe restam).
 *
 * É serializável porque uma lista de PlayerState compõe o GameState
 * que trafega entre servidor e clientes via Java RMI.
 */
package br.uesc.quoridor.common;

import java.io.Serializable;

/**
 * Fotografia de um jogador em um determinado instante da partida.
 *
 * @param id     identificador do jogador (0..3), atribuído na ordem de entrada
 * @param name   nome informado pelo jogador ao entrar na partida
 * @param row    linha atual do peão (0..8)
 * @param col    coluna atual do peão (0..8)
 * @param walls  quantidade de paredes que o jogador ainda pode colocar
 * @param symbol símbolo que representa o jogador nos tabuleiros ('1' a '4')
 */
public record PlayerState(
        int id,
        String name,
        int row,
        int col,
        int walls,
        char symbol
) implements Serializable {
    // Record: o compilador gera automaticamente os métodos de acesso
    // id(), name(), row(), col(), walls() e symbol(), além de equals(),
    // hashCode() e toString().
}
