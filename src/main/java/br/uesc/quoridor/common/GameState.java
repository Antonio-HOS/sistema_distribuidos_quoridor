package br.uesc.quoridor.common;

import java.io.Serializable;
import java.util.List;

public record GameState(
        int size,
        List<PlayerState> players,
        List<Wall> walls,
        int currentPlayer,
        boolean started,
        Integer winner,
        String message,
        List<Cell> legalMoves
) implements Serializable {
}
