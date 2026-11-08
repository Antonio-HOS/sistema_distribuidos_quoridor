/*
 * Função deste arquivo: definir o tipo Wall, que representa uma parede
 * colocada (ou a ser colocada) no tabuleiro do Quoridor.
 *
 * É serializável porque instâncias de Wall são enviadas ao servidor na
 * chamada remota GameService.placeWall(...) e também retornam dentro
 * da lista de paredes do GameState.
 */
package br.uesc.quoridor.common;

import java.io.Serializable;

/**
 * Parede do tabuleiro.
 *
 * <p>A posição é o canto superior esquerdo da parede, com linha e coluna
 * no intervalo 0..7. Cada parede cobre duas arestas adjacentes: uma
 * parede horizontal bloqueia a passagem entre as linhas {@code row} e
 * {@code row + 1} nas colunas {@code col} e {@code col + 1}; uma vertical
 * bloqueia a passagem entre as colunas {@code col} e {@code col + 1} nas
 * linhas {@code row} e {@code row + 1}.</p>
 *
 * @param row         linha do canto superior esquerdo da parede (0..7)
 * @param col         coluna do canto superior esquerdo da parede (0..7)
 * @param orientation orientação da parede (horizontal ou vertical)
 */
public record Wall(int row, int col, WallOrientation orientation) implements Serializable {
    // Record: o compilador gera automaticamente os métodos de acesso
    // row(), col() e orientation(), além de equals(), hashCode() e
    // toString(). O equals() gerado é importante: ele permite ao
    // servidor detectar paredes duplicadas com List.contains(...).
}
