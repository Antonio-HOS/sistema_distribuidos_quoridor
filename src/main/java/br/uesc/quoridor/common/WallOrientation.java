/*
 * Função deste arquivo: definir o tipo WallOrientation, que indica a
 * orientação de uma parede do Quoridor (horizontal ou vertical).
 *
 * É serializável porque faz parte do record Wall, que trafega entre
 * clientes e servidor via Java RMI.
 */
package br.uesc.quoridor.common;

import java.io.Serializable;

/**
 * Orientação de uma parede no tabuleiro.
 */
public enum WallOrientation implements Serializable {
    /** Parede deitada: bloqueia a passagem vertical entre duas linhas. */
    HORIZONTAL,
    /** Parede em pé: bloqueia a passagem horizontal entre duas colunas. */
    VERTICAL
}
