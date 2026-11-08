/*
 * Função deste arquivo: definir o tipo Direction, que representa as
 * quatro direções ortogonais de movimento de um peão no Quoridor
 * (cima, baixo, esquerda e direita).
 *
 * É serializável porque valores de Direction são enviados ao servidor
 * como parâmetro da chamada remota GameService.move(...).
 */
package br.uesc.quoridor.common;

import java.io.Serializable;

/**
 * Direção de movimento de um peão no tabuleiro.
 */
public enum Direction implements Serializable {
    /** Movimento para a linha anterior (em direção ao topo). */
    UP,
    /** Movimento para a linha seguinte (em direção à base). */
    DOWN,
    /** Movimento para a coluna anterior (em direção à esquerda). */
    LEFT,
    /** Movimento para a coluna seguinte (em direção à direita). */
    RIGHT;

    /**
     * Converte um texto digitado pelo usuário (em português ou inglês,
     * por extenso ou abreviado) na direção correspondente.
     * Usado principalmente pelo cliente em modo console.
     *
     * @param value texto a converter, ex.: "cima", "up" ou "u"
     * @return a direção correspondente ao texto
     * @throws IllegalArgumentException se o texto não for uma direção válida
     */
    public static Direction from(String value) {
        return switch (value.toLowerCase()) {
            case "cima", "up", "u" -> UP;
            case "baixo", "down", "d" -> DOWN;
            case "esquerda", "left", "l" -> LEFT;
            case "direita", "right", "r" -> RIGHT;
            default -> throw new IllegalArgumentException("Direção inválida.");
        };
    }
}
