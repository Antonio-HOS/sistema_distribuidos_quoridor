package br.uesc.quoridor.common;

import java.io.Serializable;

public record Wall(int row, int col, WallOrientation orientation) implements Serializable {
}
