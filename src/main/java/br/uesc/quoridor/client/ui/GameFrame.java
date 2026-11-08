/*
 * Função deste arquivo: ser a janela principal do cliente gráfico (Swing).
 *
 * Ela junta tudo do lado do cliente com interface gráfica: mostra o
 * tabuleiro (BoardPanel) e o painel lateral (jogadores, modo, direções),
 * traduz cliques e botões em chamadas remotas ao servidor via RMI
 * (move, moveTo e placeWall de GameService, sempre em SwingWorker para
 * não travar a tela), consulta o estado do jogo a cada 700 ms (polling)
 * para se atualizar sozinha, e trata os fins de partida (vitória e
 * desconexão). Também é aqui que fica o launch(...), que abre o
 * ConnectDialog, faz o lookup RMI e o join antes de criar esta janela.
 * Nenhuma regra do jogo é decidida aqui: tudo é validado no servidor.
 */
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

    /** Cor de fundo do painel lateral. */
    private static final Color SIDE_BG = new Color(0x243044);

    /** Cor principal dos textos do painel lateral. */
    private static final Color TEXT = new Color(0xE8EEF5);

    /** Cor dos textos secundários/apagados do painel lateral. */
    private static final Color MUTED = new Color(0xA8B4C4);

    /** Cor de destaque (ex.: aviso "Sua vez!"). */
    private static final Color ACCENT = new Color(0x3D8BFF);

    /** Stub RMI do objeto remoto do servidor (obtido via lookup). */
    private final GameService service;

    /** ID deste jogador na partida (0..3). */
    private final int playerId;

    /** Painel que desenha o tabuleiro e captura os cliques de jogada. */
    private final BoardPanel board = new BoardPanel();

    /** Rótulo com a última mensagem do servidor (jogada, espera, erro local). */
    private final JLabel statusLabel = new JLabel(" ");

    /** Rótulo que indica de quem é a vez (ou a situação da partida). */
    private final JLabel turnLabel = new JLabel(" ");

    /** Área de texto com a lista de jogadores, posições e paredes. */
    private final JTextArea playersArea = new JTextArea(6, 22);

    /** Botão de modo: clicar em casa move o peão. */
    private final JToggleButton moveMode = new JToggleButton("Mover");

    /** Botão de modo: clicar num vão coloca parede horizontal. */
    private final JToggleButton wallH = new JToggleButton("Parede H");

    /** Botão de modo: clicar num vão coloca parede vertical. */
    private final JToggleButton wallV = new JToggleButton("Parede V");

    /** Botões de direção: [0]=cima, [1]=esquerda, [2]=direita, [3]=baixo. */
    private final JButton[] dirButtons = new JButton[4];

    /** Indica se há uma chamada RMI de jogada em andamento (evita cliques duplos). */
    private final AtomicBoolean busy = new AtomicBoolean(false);

    /** Temporizador do polling que atualiza a tela a cada 700 ms. */
    private final Timer pollTimer;

    /** Garante que o aviso de saída (leave) seja enviado uma única vez. */
    private final AtomicBoolean left = new AtomicBoolean(false);

    /** Último estado recebido do servidor. */
    private GameState lastState;

    /** Garante que o diálogo de fim de partida seja exibido uma única vez. */
    private boolean endDialogShown;

    /**
     * Monta a janela do jogo: liga os callbacks do tabuleiro a esta
     * classe, coloca o tabuleiro no centro e o painel lateral à direita,
     * cria o temporizador de atualização periódica e registra o aviso
     * de saída quando a janela fecha ou o processo termina.
     *
     * @param service    stub RMI do servidor
     * @param playerId   ID deste jogador (0..3)
     * @param playerName nome deste jogador (usado no título da janela)
     */
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

        // Polling: enquanto espera os outros, a tela se atualiza sozinha.
        pollTimer = new Timer(700, e -> refreshAsync(false));
        pollTimer.setInitialDelay(200);

        // Ao fechar a janela, para o polling e avisa o servidor da saída.
        addWindowListener(new java.awt.event.WindowAdapter() {
            // Para o temporizador e registra a saída deste jogador.
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                pollTimer.stop();
                leaveQuietly();
            }
        });
        // Se o processo morrer sem fechar a janela, também avisa o servidor.
        Runtime.getRuntime().addShutdownHook(new Thread(this::leaveQuietly, "quoridor-leave"));

        pack();
        setLocationRelativeTo(null);
        setMinimumSize(getSize());
    }

    /**
     * Exibe a janela, faz uma primeira atualização imediata do estado e
     * liga o polling periódico.
     */
    public void start() {
        setVisible(true);
        refreshAsync(true);
        pollTimer.start();
    }

    /**
     * Constrói o painel lateral da janela: título, aviso de vez, mensagem
     * do servidor, lista de jogadores, seletor de modo (mover/parede),
     * botões de direção e a dica de como jogar.
     *
     * @return o painel lateral pronto para ser adicionado à janela
     */
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

        // Empilha as seções do painel de cima para baixo.
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

    /**
     * Cria um rótulo de título de seção do painel lateral (texto em
     * maiúsculas, pequeno e apagado).
     *
     * @param text texto da seção
     * @return o rótulo formatado
     */
    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /**
     * Cria um rótulo de texto longo (dica), quebrado em várias linhas
     * usando HTML com largura fixa.
     *
     * @param text texto da dica
     * @return o rótulo formatado
     */
    private JLabel hintLabel(String text) {
        JLabel l = new JLabel("<html><body style='width:200px'>" + text + "</body></html>");
        l.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        l.setForeground(MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /**
     * Constrói o seletor de modo de interação: três botões de alternância
     * mutuamente exclusivos (Mover, Parede H, Parede V) que trocam o modo
     * do tabuleiro. O modo inicial é Mover.
     *
     * @return painel com os três botões de modo
     */
    private JPanel buildModePanel() {
        // ButtonGroup garante que só um modo fique selecionado por vez.
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

    /**
     * Aplica a formatação visual padrão a um botão de modo.
     *
     * @param b botão a formatar
     */
    private void styleToggle(JToggleButton b) {
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
    }

    /**
     * Constrói o painel de direções em formato de cruz (grade 3x3):
     * ↑ no topo, ← e → no meio e ↓ embaixo, com espaçadores nos cantos
     * e no centro. Cada seta move o peão naquela direção (passo ou salto).
     *
     * @return painel com os quatro botões de direção
     */
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

    /**
     * Cria um painel vazio e transparente, usado como espaçador nas
     * posições sem botão da grade de direções.
     *
     * @return painel espaçador
     */
    private JPanel spacer() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    /**
     * Cria um botão de seta que, ao ser clicado, tenta mover o peão na
     * direção correspondente (via chamada remota move).
     *
     * @param label     símbolo da seta exibido no botão
     * @param direction direção do movimento
     * @return o botão criado
     */
    private JButton dirButton(String label, Direction direction) {
        JButton b = new JButton(label);
        b.setFont(new Font("Segoe UI", Font.BOLD, 16));
        b.setFocusPainted(false);
        b.addActionListener(e -> doMove(direction));
        return b;
    }

    /**
     * Trata o clique numa casa do tabuleiro (callback do BoardPanel):
     * confere se a casa está entre os movimentos legais calculados pelo
     * servidor e, se estiver, pede o movimento para ela. Cliques em
     * casas ilegais apenas geram um aviso no painel lateral.
     *
     * @param cell array [linha, coluna] da casa clicada
     */
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

    /**
     * Trata o clique num vão de parede do tabuleiro (callback do
     * BoardPanel): monta a Wall com a orientação do modo atual
     * (H ou V) e pede a sua colocação ao servidor.
     *
     * @param row linha do canto superior esquerdo da parede (0..7)
     * @param col coluna do canto superior esquerdo da parede (0..7)
     */
    private void onWallClick(Integer row, Integer col) {
        if (!canAct()) {
            return;
        }
        WallOrientation orientation = board.getMode() == BoardPanel.InteractionMode.WALL_H
                ? WallOrientation.HORIZONTAL
                : WallOrientation.VERTICAL;
        doPlaceWall(new Wall(row, col, orientation));
    }

    /**
     * Verifica se o jogador local pode jogar agora: partida iniciada,
     * sem vencedor, sem abandono e sendo a vez dele. Quando não pode,
     * deixa no painel lateral a explicação correspondente.
     *
     * @return {@code true} se uma jogada pode ser enviada ao servidor
     */
    private boolean canAct() {
        if (lastState == null) {
            return false;
        }
        if (lastState.abandoned()) {
            statusLabel.setText(lastState.message() == null
                    ? "Um jogador se desconectou. Partida encerrada."
                    : lastState.message());
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

    /**
     * Envia ao servidor, em segundo plano (SwingWorker, para não travar
     * a interface durante a chamada RMI), o movimento por direção
     * (usado pelos botões de seta). Ao concluir, aplica o novo estado
     * ou mostra o erro devolvido pelo servidor.
     *
     * @param direction direção do movimento
     */
    private void doMove(Direction direction) {
        if (!canAct() || !busy.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            // Chamada remota feita fora da thread da interface.
            @Override
            protected GameState doInBackground() throws Exception {
                return service.move(playerId, direction);
            }

            // De volta à thread da interface: aplica o resultado ou o erro.
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

    /**
     * Envia ao servidor, em segundo plano (SwingWorker), o movimento
     * para uma casa exata (usado no clique em casa, incluindo saltos e
     * desvios). Ao concluir, aplica o novo estado ou mostra o erro.
     *
     * @param row linha de destino (0..8)
     * @param col coluna de destino (0..8)
     */
    private void doMoveTo(int row, int col) {
        if (!canAct() || !busy.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            // Chamada remota feita fora da thread da interface.
            @Override
            protected GameState doInBackground() throws Exception {
                return service.moveTo(playerId, row, col);
            }

            // De volta à thread da interface: aplica o resultado ou o erro.
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

    /**
     * Envia ao servidor, em segundo plano (SwingWorker), a colocação de
     * uma parede. Se der certo, volta automaticamente ao modo Mover;
     * se o servidor rejeitar, mostra o motivo e atualiza a tela.
     *
     * @param wall parede a colocar
     */
    private void doPlaceWall(Wall wall) {
        if (!canAct() || !busy.compareAndSet(false, true)) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            // Chamada remota feita fora da thread da interface.
            @Override
            protected GameState doInBackground() throws Exception {
                return service.placeWall(playerId, wall);
            }

            // De volta à thread da interface: aplica o resultado ou o erro.
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

    /**
     * Busca o estado atual no servidor em segundo plano (SwingWorker) e
     * o aplica à tela. É o método do polling periódico e das atualizações
     * forçadas. Se a comunicação falhar, para o polling e avisa o usuário.
     *
     * @param force {@code true} ignora a trava de jogada em andamento
     *              (usado na abertura da janela)
     */
    private void refreshAsync(boolean force) {
        if (!force && busy.get()) {
            return;
        }
        new SwingWorker<GameState, Void>() {
            // Consulta remota feita fora da thread da interface.
            @Override
            protected GameState doInBackground() throws Exception {
                return service.getState();
            }

            // De volta à thread da interface: aplica o estado ou avisa a falha.
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

    /**
     * Aplica um estado recém-recebido do servidor: guarda como último
     * estado, repassa ao tabuleiro, atualiza o painel lateral e, se a
     * partida tiver acabado (desconexão ou vitória), mostra uma única
     * vez o diálogo de fim de jogo e para o polling.
     *
     * @param state estado recebido do servidor
     */
    private void applyState(GameState state) {
        lastState = state;
        board.setState(state);
        updateSidebar(state);

        if (state.abandoned() && !endDialogShown) {
            endDialogShown = true;
            pollTimer.stop();
            String msg = state.message() == null
                    ? "Um jogador se desconectou. Partida encerrada."
                    : state.message();
            JOptionPane.showMessageDialog(this, msg, "Jogador desconectado",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

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

    /**
     * Atualiza o painel lateral a partir do estado: mensagem do servidor,
     * aviso de vez ("Sua vez!", "Vez de...", espera ou fim de partida),
     * lista de jogadores com posição e paredes, e habilita/desabilita
     * os botões de direção e de parede conforme ser ou não a vez local.
     *
     * @param state estado recebido do servidor
     */
    private void updateSidebar(GameState state) {
        statusLabel.setText(state.message() == null ? " " : state.message());

        if (state.abandoned() || state.winner() != null) {
            turnLabel.setText("Partida encerrada");
        } else if (!state.started()) {
            turnLabel.setText("Aguardando jogadores (" + state.players().size() + "/4)");
        } else if (state.currentPlayer() == playerId) {
            turnLabel.setText("Sua vez!");
        } else {
            PlayerState current = state.players().get(state.currentPlayer());
            turnLabel.setText("Vez de " + current.name());
        }

        // Lista textual: ★ marca o jogador local e ◀ marca quem joga agora.
        StringBuilder sb = new StringBuilder();
        List<PlayerState> players = state.players();
        for (PlayerState p : players) {
            String mark = p.id() == playerId ? "★ " : "  ";
            String turn = state.started() && state.winner() == null && !state.abandoned()
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
                && !state.abandoned()
                && state.currentPlayer() == playerId;
        for (JButton b : dirButtons) {
            b.setEnabled(myTurn);
        }
        wallH.setEnabled(myTurn);
        wallV.setEnabled(myTurn);
        moveMode.setEnabled(true);
    }

    /**
     * Avisa o servidor que este jogador está saindo, no máximo uma vez e
     * sem propagar erros (daí "quietly"): é chamada ao fechar a janela e
     * no shutdown hook do processo. Também impede que o diálogo de
     * "jogador desconectado" apareça para quem está saindo.
     */
    private void leaveQuietly() {
        if (!left.compareAndSet(false, true)) {
            return;
        }
        endDialogShown = true;
        try {
            service.leave(playerId);
        } catch (RemoteException ignored) {
            // Servidor já inacessível: não há mais nada a avisar.
        }
    }

    /**
     * Mostra ao usuário, num diálogo de aviso, o motivo de uma jogada
     * rejeitada pelo servidor (extraído da causa raiz da exceção).
     *
     * @param ex exceção da chamada remota que falhou
     */
    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, rootMessage(ex),
                "Jogada inválida", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Extrai a mensagem da causa raiz de uma exceção. Necessário porque
     * as exceções de SwingWorker/RMI vêm embrulhadas em várias camadas,
     * e a mensagem útil é a mais interna (a que o servidor lançou).
     *
     * @param ex exceção recebida
     * @return mensagem da causa raiz, ou a sua representação textual
     */
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
     *
     * <p>Fluxo: define a aparência do sistema operacional, interpreta
     * host/porta dos argumentos como valores iniciais do diálogo, exibe
     * o ConnectDialog e, se confirmado, faz em segundo plano o lookup de
     * "Quoridor" no Registry e o join no servidor; com o ID em mãos,
     * cria e mostra o GameFrame. Qualquer falha é mostrada em diálogo e
     * encerra o processo.</p>
     *
     * @param args argumentos restantes da linha de comando: [host] [porta]
     */
    public static void launch(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Sem aparência do sistema, o Swing usa a aparência padrão.
        }

        String defaultHost = args.length >= 1 ? args[0] : "localhost";
        int defaultPort = 1099;
        if (args.length >= 2) {
            try {
                defaultPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // Porta inválida no argumento: mantém o padrão 1099.
            }
        }

        final String hostArg = defaultHost;
        final int portArg = defaultPort;

        // Toda a criação de componentes Swing acontece na thread de eventos.
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

            // Lookup + join em segundo plano, para não travar a interface.
            new SwingWorker<Object[], Void>() {
                // Conecta ao Registry, faz lookup e entra na partida.
                @Override
                protected Object[] doInBackground() throws Exception {
                    var registry = java.rmi.registry.LocateRegistry.getRegistry(host, port);
                    GameService service = (GameService) registry.lookup("Quoridor");
                    int id = service.join(name);
                    return new Object[]{service, id};
                }

                // Com o stub e o ID em mãos, abre a janela do jogo;
                // se a conexão falhou, avisa e encerra o processo.
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
