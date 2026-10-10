package br.uesc.quoridor.common;

import java.io.Serializable;

public record PlayerState(
        int id,
        String name,
        int row,
        int col,
        int walls,
        char symbol
) implements Serializable {
}
