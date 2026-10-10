package br.uesc.quoridor.client.ui;

import br.uesc.quoridor.common.Cell;
import br.uesc.quoridor.common.Direction;
import br.uesc.quoridor.common.GameService;
import br.uesc.quoridor.common.GameState;
import br.uesc.quoridor.common.PlayerState;
import br.uesc.quoridor.common.Wall;
import br.uesc.quoridor.common.WallOrientation;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.rmi.RemoteException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Janela principal do cliente gráfico Quoridor.
 */
public class GameFrame extends JFrame {

    private static final Color SIDE_BG = new Color(0x243044);
    private static final Color TEXT = new Color(0xE8EEF5);
    private static final Color MUTED = new Color(0xA8B4C4);
    private static final Color ACCENT = new Color(0x3D8BFF);

    private final GameService service;
    private final int playerId;
    private final BoardPanel board = new BoardPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel turnLabel = new JLabel(" ");
    private final JTextArea playersArea = new JTextArea(6, 22);
    private final JToggleButton moveMode = new JToggleButton("Mover");
    private final JToggleButton wallH = new JToggleButton("Parede H");
    private final JToggleButton wallV = new JToggleButton("Parede V");
    private final JButton[] dirButtons = new JButton[4];
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final Timer pollTimer;

    private GameState lastState;
    private boolean endDialogShown;

    public GameFrame(GameService service, int playerId, String playerName) {
        super("Quoridor — " + playerName + " (P" + (playerId + 1) + ")");
        this.service = service;
        this.playerId = playerId;

        board.setMyPlayerId(playerId);
        board.setOnCellClick(this::onCellClick);
        board.setOnWallClick(this::onWallClick);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(new Color(0x1A2332));

        add(board, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);

        pollTimer = new Timer(700, e -> refreshAsync(false));
        pollTimer.setInitialDelay(200);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                pollTimer.stop();
                try {
                    service.leave(playerId);
                } catch (RemoteException ignored) {
                }
            }
        });

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(getSize());
    }

    public void start() {
        setVisible(true);
        refreshAsync(true);
        pollTimer.start();
    }

    private JPanel buildSidePanel() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(SIDE_BG);
        side.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        side.setPreferredSize(new Dimension(260, 0));

        JLabel title = new JLabel("Quoridor");
        title.setFont(new Font("Georgia", Font.BOLD, 22));
        title.setForeground(TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);

        turnLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        turnLabel.setForeground(ACCENT);
        turnLabel.setAlignmentX(LEFT_ALIGNMENT);

        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(MUTED);
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);

        playersArea.setEditable(false);
        playersArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        playersArea.setBackground(new Color(0x1A2332));
        playersArea.setForeground(TEXT);
        playersArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(playersArea);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(230, 130));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x3A4A5C)));

        side.add(title);
        side.add(Box.createVerticalStrut(8));
        side.add(turnLabel);
        side.add(Box.createVerticalStrut(4));
        side.add(statusLabel);
        side.add(Box.createVerticalStrut(12));
        side.add(sectionLabel("Jogadores"));
        side.add(Box.createVerticalStrut(4));
        side.add(scroll);
        side.add(Box.createVerticalStrut(14));
        side.add(sectionLabel("Modo"));
        side.add(Box.createVerticalStrut(6));
        side.add(buildModePanel());
        side.add(Box.createVerticalStrut(14));
        side.add(sectionLabel("Direção"));
        side.add(Box.createVerticalStrut(6));
        side.add(buildDirectionPad());
        side.add(Box.createVerticalStrut(14));
        side.add(sectionLabel("Dica"));
        side.add(Box.createVerticalStrut(4));
        side.add(hintLabel(
                "As casas em azul são jogadas válidas. "
                        + "Com uma peça à frente, clique atrás dela para pular, "
                        + "ou ao lado se uma parede bloquear a passagem."));
        side.add(Box.createVerticalGlue());

        return side;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel hintLabel(String text) {
        JLabel l = new JLabel("<html><body style='width:200px'>" + text + "</body></html>");
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JPanel buildModePanel() {
        ButtonGroup group = new ButtonGroup();
        group.add(moveMode);
        group.add(wallH);
        group.add(wallV);
        moveMode.setSelected(true);

        styleToggle(moveMode);
        styleToggle(wallH);
        styleToggle(wallV);

        moveMode.addActionListener(e -> board.setMode(BoardPanel.InteractionMode.MOVE));
        wallH.addActionListener(e -> board.setMode(BoardPanel.InteractionMode.WALL_H));
        wallV.addActionListener(e -> board.setMode(BoardPanel.InteractionMode.WALL_V));

        JPanel panel = new JPanel(new GridLayout(3, 1, 4, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        panel.add(moveMode);
        panel.add(wallH);
        panel.add(wallV);
        return panel;
    }

    private void styleToggle(JToggleButton b) {
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
    }

    private JPanel buildDirectionPad() {
        JPanel pad = new JPanel(new GridLayout(3, 3, 4, 4));
        pad.setOpaque(false);
        pad.setAlignmentX(LEFT_ALIGNMENT);
        pad.setMaximumSize(new Dimension(160, 120));

        dirButtons[0] = dirButton("↑", Direction.UP);
        dirButtons[1] = dirButton("←", Direction.LEFT);
        dirButtons[2] = dirButton("→", Direction.RIGHT);
        dirButtons[3] = dirButton("↓", Direction.DOWN);

        pad.add(spacer());
        pad.add(dirButtons[0]);
        pad.add(spacer());
        pad.add(dirButtons[1]);
        pad.add(spacer());
        pad.add(dirButtons[2]);
        pad.add(spacer());
        pad.add(dirButtons[3]);
        pad.add(spacer());
        return pad;
    }

    private JPanel spacer() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    private JButton dirButton(String label, Direction direction) {
        JButton b = new JButton(label);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setFocusPainted(false);
        b.addActionListener(e -> doMove(direction));
        return b;
    }

    private void onCellClick(int[] cell) {
        if (board.getMode() != BoardPanel.InteractionMode.MOVE || lastState == null) {
            return;
        }
        if (!canAct()) {
            return;
        }

        boolean legal = false;
        if (lastState.legalMoves() != null) {
            for (Cell candidate : lastState.legalMoves()) {
                if (candidate.row() == cell[0] && candidate.col() == cell[1]) {
                    legal = true;
                    break;
                }
            }
        }

        if (!legal) {
            statusLabel.setText("Essa casa não é um movimento válido.");
            return;
        }
        doMoveTo(cell[0], cell[1]);
    }

    private void onWallClick(Integer row, Integer col) {
        if (!canAct()) {
            return;
        }
        WallOrientation orientation = board.getMode() == BoardPanel.InteractionMode.WALL_H
                ? WallOrientation.HORIZONTAL
                : WallOrientation.VERTICAL;
        doPlaceWall(new Wall(row, col, orientation));
    }

    private boolean canAct() {
        if (lastState == null) {
            return false;
        }
        if (!lastState.started()) {
            statusLabel.setText("Aguardando todos os jogadores...");
            return false;
        }
        if (lastState.winner() != null) {
            statusLabel.setText("Partida encerrada.");
            return false;
        }
        if (lastState.currentPlayer() != playerId) {
            statusLabel.setText("Aguarde a sua vez.");
            return false;
        }
        return true;
    }

    private void doMove(Direction direction) {
        if (!canAct() || !busy.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            @Override
            protected GameState doInBackground() throws Exception {
                return service.move(playerId, direction);
            }

            @Override
            protected void done() {
                busy.set(false);
                try {
                    applyState(get());
                } catch (Exception ex) {
                    showError(ex);
                    refreshAsync(false);
                }
            }
        }.execute();
    }

    private void doMoveTo(int row, int col) {
        if (!canAct() || !busy.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            @Override
            protected GameState doInBackground() throws Exception {
                return service.moveTo(playerId, row, col);
            }

            @Override
            protected void done() {
                busy.set(false);
                try {
                    applyState(get());
                } catch (Exception ex) {
                    showError(ex);
                    refreshAsync(false);
                }
            }
        }.execute();
    }

    private void doPlaceWall(Wall wall) {
        if (!canAct() || !busy.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            @Override
            protected GameState doInBackground() throws Exception {
                return service.placeWall(playerId, wall);
            }

            @Override
            protected void done() {
                busy.set(false);
                try {
                    applyState(get());
                    moveMode.setSelected(true);
                    board.setMode(BoardPanel.InteractionMode.MOVE);
                } catch (Exception ex) {
                    showError(ex);
                    refreshAsync(false);
                }
            }
        }.execute();
    }

    private void refreshAsync(boolean force) {
        if (!force && busy.get()) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            @Override
            protected GameState doInBackground() throws Exception {
                return service.getState();
            }

            @Override
            protected void done() {
                try {
                    applyState(get());
                } catch (Exception ex) {
                    pollTimer.stop();
                    JOptionPane.showMessageDialog(GameFrame.this,
                            "Falha na comunicação com o servidor:\n" + rootMessage(ex),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void applyState(GameState state) {
        lastState = state;
        board.setState(state);
        updateSidebar(state);

        if (state.winner() != null && !endDialogShown) {
            endDialogShown = true;
            pollTimer.stop();
            PlayerState winner = state.players().get(state.winner());
            String msg = winner.name() + " venceu a partida!";
            if (state.winner() == playerId) {
                msg = "Você venceu!";
            }
            JOptionPane.showMessageDialog(this, msg, "Fim de jogo",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void updateSidebar(GameState state) {
        statusLabel.setText(state.message() == null ? " " : state.message());

        if (!state.started()) {
            turnLabel.setText("Aguardando jogadores (" + state.players().size() + "/4)");
        } else if (state.winner() != null) {
            turnLabel.setText("Partida encerrada");
        } else if (state.currentPlayer() == playerId) {
            turnLabel.setText("Sua vez!");
        } else {
            PlayerState current = state.players().get(state.currentPlayer());
            turnLabel.setText("Vez de " + current.name());
        }

        StringBuilder sb = new StringBuilder();
        List<PlayerState> players = state.players();
        for (PlayerState p : players) {
            String mark = p.id() == playerId ? "★ " : "  ";
            String turn = state.started() && state.winner() == null
                    && p.id() == state.currentPlayer() ? " ◀" : "";
            sb.append(mark)
                    .append("P").append(p.id() + 1)
                    .append(" ").append(p.name())
                    .append("\n   pos=(").append(p.row()).append(",").append(p.col()).append(")")
                    .append("  paredes=").append(p.walls())
                    .append(turn)
                    .append("\n");
        }
        playersArea.setText(sb.toString());

        boolean myTurn = state.started()
                && state.winner() == null
                && state.currentPlayer() == playerId;
        for (JButton b : dirButtons) {
            b.setEnabled(myTurn);
        }
        wallH.setEnabled(myTurn);
        wallV.setEnabled(myTurn);
        moveMode.setEnabled(true);
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, rootMessage(ex),
                "Jogada inválida", JOptionPane.WARNING_MESSAGE);
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        return msg == null || msg.isBlank() ? t.toString() : msg;
    }

    /**
     * Abre o diálogo de conexão, faz join RMI e inicia a janela do jogo.
     */
    public static void launch(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        String defaultHost = args.length >= 1 ? args[0] : "localhost";
        int defaultPort = 1099;
        if (args.length >= 2) {
            try {
                defaultPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        final String hostArg = defaultHost;
        final int portArg = defaultPort;

        SwingUtilities.invokeLater(() -> {
            ConnectDialog dialog = new ConnectDialog(null, hostArg, portArg);
            dialog.setVisible(true);
            if (!dialog.isConfirmed()) {
                System.exit(0);
                return;
            }

            String name = dialog.getPlayerName();
            String host = dialog.getHost();
            int port = dialog.getPort();

            new SwingWorker<Object[], Void>() {
                @Override
                protected Object[] doInBackground() throws Exception {
                    var registry = java.rmi.registry.LocateRegistry.getRegistry(host, port);
                    GameService service = (GameService) registry.lookup("Quoridor");
                    int id = service.join(name);
                    return new Object[]{service, id};
                }

                @Override
                protected void done() {
                    try {
                        Object[] result = get();
                        GameService service = (GameService) result[0];
                        int id = (Integer) result[1];
                        GameFrame frame = new GameFrame(service, id, name);
                        frame.start();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(null,
                                "Não foi possível conectar:\n" + rootMessage(ex),
                                "Erro", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
                }
            }.execute();
        });
    }
}
