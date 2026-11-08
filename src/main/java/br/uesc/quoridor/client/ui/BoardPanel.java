/*
 * Função deste arquivo: desenhar o tabuleiro 9x9 do Quoridor na interface
 * gráfica Swing e traduzir os cliques do mouse em intenções de jogada.
 *
 * Este componente só cuida de apresentação e interação: ele recebe um
 * GameState pronto do servidor (via GameFrame), pinta casas, paredes,
 * peões e os movimentos legais destacados, e avisa o GameFrame (por
 * callbacks) quando o usuário clica numa casa ou num vão de parede.
 * Nenhuma regra do jogo é decidida aqui.
 */
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

    /** Número de casas por lado do tabuleiro. */
    private static final int SIZE = 9;

    /** Tamanho, em pixels, do lado de cada casa. */
    private static final int CELL = 46;

    /** Largura, em pixels, do vão entre casas (onde as paredes se encaixam). */
    private static final int GAP = 14;

    /** Margem, em pixels, ao redor do tabuleiro (onde vão os rótulos 0..8). */
    private static final int MARGIN = 28;

    /** Cor de fundo do painel. */
    private static final Color BG = new Color(0x1A2332);

    /** Cor de preenchimento das casas. */
    private static final Color CELL_FILL = new Color(0xE8D5B7);

    /** Cor da borda das casas. */
    private static final Color CELL_EDGE = new Color(0xC4A882);

    /** Cor das paredes (colocadas e pré-visualizadas). */
    private static final Color WALL_COLOR = new Color(0x6B4423);

    /** Cor dos rótulos de coordenadas. */
    private static final Color LABEL = new Color(0xA8B4C4);

    /** Cor de cada peão, indexada pelo ID do jogador (P1..P4). */
    private static final Color[] PAWN = {
            new Color(0xE74C3C),
            new Color(0x2980B9),
            new Color(0x27AE60),
            new Color(0xF39C12)
    };

    /**
     * Modo de interação atual do tabuleiro: o que um clique significa.
     */
    public enum InteractionMode {
        /** Clique numa casa = tentativa de mover o peão para ela. */
        MOVE,
        /** Clique num vão horizontal = colocar parede horizontal. */
        WALL_H,
        /** Clique num vão vertical = colocar parede vertical. */
        WALL_V
    }

    /** Último estado recebido do servidor; {@code null} antes da 1ª consulta. */
    private GameState state;

    /** ID do jogador dono desta janela (-1 enquanto não definido). */
    private int myPlayerId = -1;

    /** Modo de interação atual (mover ou colocar parede H/V). */
    private InteractionMode mode = InteractionMode.MOVE;

    /** Linha da parede sob o mouse (pré-visualização); {@code null} se nenhuma. */
    private Integer hoverWallRow;

    /** Coluna da parede sob o mouse (pré-visualização); {@code null} se nenhuma. */
    private Integer hoverWallCol;

    /** Callback avisado quando uma casa é clicada no modo MOVE: recebe [linha, coluna]. */
    private Consumer<int[]> onCellClick;       // [row, col]

    /** Callback avisado quando um vão de parede é clicado nos modos WALL_H/WALL_V. */
    private BiConsumer<Integer, Integer> onWallClick; // row, col

    /**
     * Cria o painel do tabuleiro com tamanho preferido calculado a partir
     * das casas, vãos e margens, e registrando os ouvintes de mouse que
     * tratam cliques (jogada) e movimento (pré-visualização de parede).
     */
    public BoardPanel() {
        setBackground(BG);
        setPreferredSize(new Dimension(
                MARGIN * 2 + SIZE * CELL + (SIZE - 1) * GAP,
                MARGIN * 2 + SIZE * CELL + (SIZE - 1) * GAP
        ));

        // Ouvinte único reutilizado para cliques e movimento do mouse.
        MouseAdapter mouse = new MouseAdapter() {
            // Clique: interpreta conforme o modo atual (casa ou parede).
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }

            // Movimento: atualiza a pré-visualização da parede sob o mouse.
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }

            // Saída do painel: esconde a pré-visualização de parede.
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

    /**
     * Atualiza o estado exibido (recebido do servidor) e manda redesenhar.
     *
     * @param state novo estado da partida
     */
    public void setState(GameState state) {
        this.state = state;
        repaint();
    }

    /**
     * Informa qual é o jogador dono desta janela, para destacar o seu
     * peão e só mostrar os movimentos legais na vez dele.
     *
     * @param myPlayerId ID do jogador local (0..3)
     */
    public void setMyPlayerId(int myPlayerId) {
        this.myPlayerId = myPlayerId;
    }

    /**
     * Troca o modo de interação (mover / parede H / parede V), limpando
     * qualquer pré-visualização de parede em andamento.
     *
     * @param mode novo modo de interação
     */
    public void setMode(InteractionMode mode) {
        this.mode = mode;
        hoverWallRow = null;
        hoverWallCol = null;
        repaint();
    }

    /**
     * Devolve o modo de interação atual.
     *
     * @return modo atual (MOVE, WALL_H ou WALL_V)
     */
    public InteractionMode getMode() {
        return mode;
    }

    /**
     * Registra quem deve ser avisado quando uma casa for clicada.
     *
     * @param onCellClick callback que recebe um array [linha, coluna]
     */
    public void setOnCellClick(Consumer<int[]> onCellClick) {
        this.onCellClick = onCellClick;
    }

    /**
     * Registra quem deve ser avisado quando um vão de parede for clicado.
     *
     * @param onWallClick callback que recebe (linha, coluna) do canto da parede
     */
    public void setOnWallClick(BiConsumer<Integer, Integer> onWallClick) {
        this.onWallClick = onWallClick;
    }

    /**
     * Converte uma coluna do tabuleiro na coordenada X (em pixels) do
     * canto esquerdo da casa correspondente.
     *
     * @param col coluna (0..8)
     * @return posição X da casa no painel
     */
    private int cellX(int col) {
        return MARGIN + col * (CELL + GAP);
    }

    /**
     * Converte uma linha do tabuleiro na coordenada Y (em pixels) do
     * canto superior da casa correspondente.
     *
     * @param row linha (0..8)
     * @return posição Y da casa no painel
     */
    private int cellY(int row) {
        return MARGIN + row * (CELL + GAP);
    }

    /**
     * Trata um clique do mouse conforme o modo atual: no modo MOVE,
     * procura a casa clicada e avisa o callback de casa; nos modos de
     * parede, procura o vão clicado e avisa o callback de parede.
     * Cliques fora de qualquer casa/vão são simplesmente ignorados.
     *
     * @param x coordenada X do clique, em pixels
     * @param y coordenada Y do clique, em pixels
     */
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

    /**
     * Atualiza qual parede está sob o ponteiro do mouse, para desenhar a
     * sua pré-visualização. No modo MOVE não há pré-visualização. Só
     * redesenha quando a parede sob o mouse realmente muda.
     *
     * @param x coordenada X do mouse, em pixels
     * @param y coordenada Y do mouse, em pixels
     */
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

    /**
     * Descobre qual casa contém um ponto do painel, varrendo a grade 9x9
     * e comparando o ponto com o retângulo de cada casa.
     *
     * @param x coordenada X do ponto, em pixels
     * @param y coordenada Y do ponto, em pixels
     * @return array [linha, coluna] da casa, ou {@code null} se o ponto
     *         estiver num vão, na margem ou fora do tabuleiro
     */
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
     *
     * <p>No modo WALL_H, o ponto precisa estar num vão horizontal com o
     * comprimento de duas casas; no modo WALL_V, num vão vertical com a
     * altura de duas casas.</p>
     *
     * @param x coordenada X do ponto, em pixels
     * @param y coordenada Y do ponto, em pixels
     * @return array [linha, coluna] do canto superior esquerdo da parede,
     *         ou {@code null} se o ponto não estiver sobre nenhum vão válido
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

    /**
     * Desenha o painel inteiro, na ordem: rótulos de coordenadas, casas,
     * destaque dos movimentos legais, paredes colocadas, pré-visualização
     * da parede sob o mouse e, por cima de tudo, os peões.
     *
     * @param g contexto gráfico fornecido pelo Swing
     */
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

    /**
     * Desenha os rótulos de coordenadas 0..8 acima das colunas e à
     * esquerda das linhas, para o jogador poder se orientar.
     *
     * @param g2 contexto gráfico 2D já configurado
     */
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

    /**
     * Desenha todas as casas do tabuleiro como retângulos arredondados
     * preenchidos, com borda.
     *
     * @param g2 contexto gráfico 2D já configurado
     */
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

    /**
     * Destaca com um círculo azul as casas para as quais o jogador local
     * pode mover o peão. Só faz isso quando é a vez dele e a partida
     * ainda não tem vencedor ou estado para exibir.
     *
     * @param g2 contexto gráfico 2D já configurado
     */
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

    /**
     * Desenha todas as paredes já colocadas no tabuleiro, na cor definitiva.
     *
     * @param g2 contexto gráfico 2D já configurado
     */
    private void drawWalls(Graphics2D g2) {
        if (state == null) {
            return;
        }
        g2.setColor(WALL_COLOR);
        for (Wall w : state.walls()) {
            paintWall(g2, w.row(), w.col(), w.orientation(), false);
        }
    }

    /**
     * Desenha, semitransparente, a parede que seria colocada se o usuário
     * clicasse agora (na orientação do modo atual). Não desenha nada
     * quando o mouse não está sobre nenhum vão válido.
     *
     * @param g2 contexto gráfico 2D já configurado
     */
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

    /**
     * Desenha uma única parede como uma linha grossa de pontas
     * arredondadas atravessando o vão entre as casas, ocupando o
     * comprimento de duas casas na orientação indicada.
     *
     * @param g2          contexto gráfico 2D já configurado
     * @param row         linha do canto superior esquerdo da parede (0..7)
     * @param col         coluna do canto superior esquerdo da parede (0..7)
     * @param orientation horizontal ou vertical
     * @param preview     {@code true} para a pré-visualização (traço mais
     *                    fino); {@code false} para parede já colocada
     */
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

    /**
     * Desenha o peão de cada jogador como um círculo colorido com o seu
     * símbolo (1..4). O peão do jogador local ganha contorno branco, e o
     * peão de quem está na vez ganha contorno dourado.
     *
     * @param g2 contexto gráfico 2D já configurado
     */
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
