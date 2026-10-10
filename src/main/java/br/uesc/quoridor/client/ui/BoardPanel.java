package br.uesc.quoridor.client.ui;

import br.uesc.quoridor.common.Cell;
import br.uesc.quoridor.common.GameState;
import br.uesc.quoridor.common.PlayerState;
import br.uesc.quoridor.common.Wall;
import br.uesc.quoridor.common.WallOrientation;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tabuleiro Quoridor 9x9 com peões e paredes.
 * Clique em célula move; em modo parede, clique no vão entre células.
 */
public class BoardPanel extends JPanel {

    private static final int SIZE = 9;
    private static final int CELL = 46;
    private static final int GAP = 14;
    private static final int MARGIN = 28;

    private static final Color BG = new Color(0x1A2332);
    private static final Color CELL_FILL = new Color(0xE8D5B7);
    private static final Color CELL_EDGE = new Color(0xC4A882);
    private static final Color WALL_COLOR = new Color(0x6B4423);
    private static final Color LABEL = new Color(0xA8B4C4);
    private static final Color[] PAWN = {
            new Color(0xE74C3C),
            new Color(0x2980B9),
            new Color(0x27AE60),
            new Color(0xF39C12)
    };

    public enum InteractionMode { MOVE, WALL_H, WALL_V }

    private GameState state;
    private int myPlayerId = -1;
    private InteractionMode mode = InteractionMode.MOVE;
    private Integer hoverWallRow;
    private Integer hoverWallCol;

    private Consumer<int[]> onCellClick;       // [row, col]
    private BiConsumer<Integer, Integer> onWallClick; // row, col

    public BoardPanel() {
        setBackground(BG);
        setPreferredSize(new Dimension(
                MARGIN * 2 + SIZE * CELL + (SIZE - 1) * GAP,
                MARGIN * 2 + SIZE * CELL + (SIZE - 1) * GAP
        ));

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverWallRow != null) {
                    hoverWallRow = null;
                    hoverWallCol = null;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setState(GameState state) {
        this.state = state;
        repaint();
    }

    public void setMyPlayerId(int myPlayerId) {
        this.myPlayerId = myPlayerId;
    }

    public void setMode(InteractionMode mode) {
        this.mode = mode;
        hoverWallRow = null;
        hoverWallCol = null;
        repaint();
    }

    public InteractionMode getMode() {
        return mode;
    }

    public void setOnCellClick(Consumer<int[]> onCellClick) {
        this.onCellClick = onCellClick;
    }

    public void setOnWallClick(BiConsumer<Integer, Integer> onWallClick) {
        this.onWallClick = onWallClick;
    }

    private int cellX(int col) {
        return MARGIN + col * (CELL + GAP);
    }

    private int cellY(int row) {
        return MARGIN + row * (CELL + GAP);
    }

    private void handleClick(int x, int y) {
        if (mode == InteractionMode.MOVE) {
            int[] cell = hitCell(x, y);
            if (cell != null && onCellClick != null) {
                onCellClick.accept(cell);
            }
            return;
        }

        int[] wall = hitWallSlot(x, y);
        if (wall != null && onWallClick != null) {
            onWallClick.accept(wall[0], wall[1]);
        }
    }

    private void updateHover(int x, int y) {
        if (mode == InteractionMode.MOVE) {
            if (hoverWallRow != null) {
                hoverWallRow = null;
                hoverWallCol = null;
                repaint();
            }
            return;
        }

        int[] wall = hitWallSlot(x, y);
        Integer nr = wall == null ? null : wall[0];
        Integer nc = wall == null ? null : wall[1];
        if ((nr == null && hoverWallRow != null)
                || (nr != null && (!nr.equals(hoverWallRow) || !nc.equals(hoverWallCol)))) {
            hoverWallRow = nr;
            hoverWallCol = nc;
            repaint();
        }
    }

    private int[] hitCell(int x, int y) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int cx = cellX(c);
                int cy = cellY(r);
                if (x >= cx && x < cx + CELL && y >= cy && y < cy + CELL) {
                    return new int[]{r, c};
                }
            }
        }
        return null;
    }

    /**
     * Detecta o canto (0..7, 0..7) da parede sob o ponteiro,
     * conforme orientação do modo atual.
     */
    private int[] hitWallSlot(int x, int y) {
        if (mode == InteractionMode.WALL_H) {
            for (int r = 0; r < SIZE - 1; r++) {
                int gy = cellY(r) + CELL;
                if (y < gy || y >= gy + GAP) {
                    continue;
                }
                for (int c = 0; c < SIZE - 1; c++) {
                    int gx = cellX(c);
                    int width = CELL * 2 + GAP;
                    if (x >= gx && x < gx + width) {
                        return new int[]{r, c};
                    }
                }
            }
        } else if (mode == InteractionMode.WALL_V) {
            for (int c = 0; c < SIZE - 1; c++) {
                int gx = cellX(c) + CELL;
                if (x < gx || x >= gx + GAP) {
                    continue;
                }
                for (int r = 0; r < SIZE - 1; r++) {
                    int gy = cellY(r);
                    int height = CELL * 2 + GAP;
                    if (y >= gy && y < gy + height) {
                        return new int[]{r, c};
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawLabels(g2);
        drawCells(g2);
        drawLegalMoves(g2);
        drawWalls(g2);
        drawWallPreview(g2);
        drawPawns(g2);

        g2.dispose();
    }

    private void drawLabels(Graphics2D g2) {
        g2.setColor(LABEL);
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        for (int i = 0; i < SIZE; i++) {
            String n = String.valueOf(i);
            int cx = cellX(i) + CELL / 2 - 3;
            g2.drawString(n, cx, MARGIN - 8);
            g2.drawString(n, 8, cellY(i) + CELL / 2 + 4);
        }
    }

    private void drawCells(Graphics2D g2) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int x = cellX(c);
                int y = cellY(r);
                RoundRectangle2D rect = new RoundRectangle2D.Float(x, y, CELL, CELL, 8, 8);
                g2.setColor(CELL_FILL);
                g2.fill(rect);
                g2.setColor(CELL_EDGE);
                g2.draw(rect);
            }
        }
    }

    private void drawLegalMoves(Graphics2D g2) {
        if (state == null
                || state.legalMoves() == null
                || myPlayerId != state.currentPlayer()
                || state.winner() != null) {
            return;
        }

        for (Cell cell : state.legalMoves()) {
            int x = cellX(cell.col());
            int y = cellY(cell.row());
            g2.setColor(new Color(61, 139, 255, 110));
            g2.fillOval(x + 16, y + 16, CELL - 32, CELL - 32);
        }
    }

    private void drawWalls(Graphics2D g2) {
        if (state == null) {
            return;
        }
        g2.setColor(WALL_COLOR);
        for (Wall w : state.walls()) {
            paintWall(g2, w.row(), w.col(), w.orientation(), false);
        }
    }

    private void drawWallPreview(Graphics2D g2) {
        if (hoverWallRow == null || hoverWallCol == null) {
            return;
        }
        WallOrientation orientation = mode == InteractionMode.WALL_H
                ? WallOrientation.HORIZONTAL
                : WallOrientation.VERTICAL;
        g2.setColor(new Color(WALL_COLOR.getRed(), WALL_COLOR.getGreen(),
                WALL_COLOR.getBlue(), 140));
        paintWall(g2, hoverWallRow, hoverWallCol, orientation, true);
    }

    private void paintWall(Graphics2D g2, int row, int col,
                           WallOrientation orientation, boolean preview) {
        float thickness = preview ? 8f : 10f;
        g2.setStroke(new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (orientation == WallOrientation.HORIZONTAL) {
            int x1 = cellX(col);
            int x2 = cellX(col + 1) + CELL;
            int y = cellY(row) + CELL + GAP / 2;
            g2.drawLine(x1 + 2, y, x2 - 2, y);
        } else {
            int y1 = cellY(row);
            int y2 = cellY(row + 1) + CELL;
            int x = cellX(col) + CELL + GAP / 2;
            g2.drawLine(x, y1 + 2, x, y2 - 2);
        }
    }

    private void drawPawns(Graphics2D g2) {
        if (state == null) {
            return;
        }
        for (PlayerState p : state.players()) {
            int x = cellX(p.col());
            int y = cellY(p.row());
            Color color = PAWN[p.id() % PAWN.length];

            boolean mine = p.id() == myPlayerId;
            boolean turn = state.started()
                    && state.winner() == null
                    && p.id() == state.currentPlayer();

            int pad = 8;
            int d = CELL - pad * 2;
            g2.setColor(color);
            g2.fillOval(x + pad, y + pad, d, d);

            if (mine || turn) {
                g2.setStroke(new BasicStroke(mine ? 3f : 2f));
                g2.setColor(mine ? Color.WHITE : new Color(0xFFD700));
                g2.drawOval(x + pad, y + pad, d, d);
            }

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
            String label = String.valueOf(p.symbol());
            int tw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x + (CELL - tw) / 2, y + CELL / 2 + 6);
        }
    }
}
