package br.uesc.quoridor.common;

import java.io.Serializable;

public enum Direction implements Serializable {
    UP, DOWN, LEFT, RIGHT;

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
