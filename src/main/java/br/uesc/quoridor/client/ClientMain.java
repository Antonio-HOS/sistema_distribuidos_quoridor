/*
 * Função deste arquivo: ser o ponto de entrada (main) do processo cliente.
 *
 * Ele oferece duas formas de jogar, ambas falando com o servidor apenas
 * via Java RMI (lookup de "Quoridor" no Registry e chamadas a GameService):
 *  - padrão: interface gráfica Swing (delegada para GameFrame.launch);
 *  - opcional: cliente em modo console/texto, ativado com --console ou -c,
 *    implementado nesta própria classe (loop de comandos, desenho do
 *    tabuleiro em texto e uma thread vigia de desconexão).
 */
package br.uesc.quoridor.client;

import br.uesc.quoridor.client.ui.GameFrame;
import br.uesc.quoridor.common.*;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cliente Quoridor.
 * Por padrão abre a interface gráfica Swing.
 * Use {@code --console} para o cliente em texto.
 */
public class ClientMain {

    /** Stub RMI do objeto remoto do servidor (obtido via lookup). */
    private final GameService service;

    /** Leitor dos comandos digitados no console. */
    private final Scanner scanner;

    /** Indica se o loop/vigia do cliente ainda devem continuar rodando. */
    private final AtomicBoolean running = new AtomicBoolean(true);

    /** Garante que o aviso de saída (leave) seja enviado uma única vez. */
    private final AtomicBoolean left = new AtomicBoolean(false);

    /** Garante que o aviso de "partida encerrada" seja mostrado uma única vez. */
    private final AtomicBoolean disconnectNotified = new AtomicBoolean(false);

    /** ID deste jogador na partida (0..3); -1 enquanto não entrou. */
    private int playerId = -1;

    /**
     * Cria o cliente de console já ligado ao stub do servidor.
     *
     * @param service stub RMI de GameService obtido no Registry
     */
    public ClientMain(GameService service) {
        this.service = service;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Ponto de entrada do processo cliente. Separa o argumento
     * {@code --console}/{@code -c} dos demais (host/porta) e decide:
     * com {@code --console}, roda o cliente em texto nesta classe; sem
     * ele, delega para a interface gráfica Swing.
     *
     * @param args argumentos da linha de comando: [--console|-c] [host] [porta]
     */
    public static void main(String[] args) {
        // Separa a flag de modo console dos argumentos host/porta.
        List<String> filtered = new ArrayList<>();
        boolean console = false;

        for (String arg : args) {
            if ("--console".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                console = true;
            } else {
                filtered.add(arg);
            }
        }

        String[] rest = filtered.toArray(new String[0]);

        if (!console) {
            GameFrame.launch(rest);
            return;
        }

        runConsole(rest);
    }

    /**
     * Executa o cliente em modo console: pergunta nome/host/porta (quando
     * não vieram por argumento), localiza o serviço "Quoridor" no RMI
     * Registry, entra na partida (join), registra o gancho de saída e
     * entra no loop de comandos.
     *
     * @param args argumentos restantes: [host] [porta], ambos opcionais
     */
    private static void runConsole(String[] args) {
        try {
            Scanner input = new Scanner(System.in);

            System.out.print("Nome do jogador: ");
            String name = input.nextLine().trim();

            String host;
            int port;

            // Host: argumento, ou pergunta com padrão "localhost".
            if (args.length >= 1) {
                host = args[0];
            } else {
                System.out.print("Host do servidor RMI [localhost]: ");
                host = input.nextLine().trim();
                if (host.isBlank()) {
                    host = "localhost";
                }
            }

            // Porta: argumento, ou pergunta com padrão 1099.
            if (args.length >= 2) {
                port = Integer.parseInt(args[1]);
            } else {
                System.out.print("Porta do Registry [1099]: ");
                String value = input.nextLine().trim();
                port = value.isBlank() ? 1099 : Integer.parseInt(value);
            }

            // Conexão RMI: obtém o Registry e faz lookup do objeto remoto.
            Registry registry = LocateRegistry.getRegistry(host, port);
            GameService service = (GameService) registry.lookup("Quoridor");

            ClientMain client = new ClientMain(service);
            client.playerId = service.join(name);
            // Se o processo for encerrado (Ctrl+C, fechar terminal...),
            // avisa o servidor para encerrar a partida para os demais.
            Runtime.getRuntime().addShutdownHook(new Thread(client::leaveQuietly, "quoridor-leave"));
            client.startWatcher();

            System.out.println();
            System.out.println("Você é o jogador " + (client.playerId + 1) + ".");
            System.out.println("Aguardando os outros jogadores...");
            client.loop();
            client.running.set(false);

        } catch (Exception e) {
            System.err.println("Erro no cliente: " + e.getMessage());
        }
    }

    /**
     * Loop principal do modo console: a cada iteração busca o estado no
     * servidor, redesenha a tela, verifica fim de partida (vitória ou
     * abandono) e interpreta o comando digitado (mover, colocar parede,
     * estado, ajuda ou sair), delegando sempre a validação ao servidor.
     */
    private void loop() {
        while (true) {
            try {
                GameState state = service.getState();
                printState(state);

                // Partida encerrada porque alguém se desconectou.
                if (state.abandoned()) {
                    running.set(false);
                    if (disconnectNotified.compareAndSet(false, true)) {
                        System.out.println("Partida encerrada.");
                    }
                    break;
                }

                if (state.winner() != null) {
                    System.out.println("Partida encerrada.");
                    break;
                }

                System.out.println();
                System.out.print("Comando > ");

                String command = scanner.nextLine().trim();

                // Comando de saída: avisa o servidor e encerra o loop.
                if (command.equalsIgnoreCase("sair")) {
                    running.set(false);
                    disconnectNotified.set(true);
                    leaveQuietly();
                    System.out.println("Você saiu. A partida foi encerrada.");
                    break;
                }

                if (command.equalsIgnoreCase("ajuda")) {
                    printHelp();
                    continue;
                }

                if (command.equalsIgnoreCase("estado")) {
                    // Apenas volta ao topo do loop, que redesenha o estado.
                    continue;
                }

                try {
                    if (command.toLowerCase().startsWith("m ")) {
                        // Movimento: "m LINHA COLUNA" (casa exata) ou
                        // "m <direção>" (cima/baixo/esquerda/direita).
                        String[] parts = command.substring(2).trim().split("\\s+");
                        if (parts.length == 2) {
                            int row = Integer.parseInt(parts[0]);
                            int col = Integer.parseInt(parts[1]);
                            state = service.moveTo(playerId, row, col);
                        } else {
                            state = service.move(
                                    playerId,
                                    Direction.from(parts[0])
                            );
                        }
                        System.out.println(state.message());

                    } else if (command.toLowerCase().startsWith("p ")) {
                        // Parede: "p h LINHA COLUNA" ou "p v LINHA COLUNA".
                        String[] parts = command.split("\\s+");

                        if (parts.length != 4) {
                            System.out.println(
                                    "Uso: p h LINHA COLUNA"
                            );
                            continue;
                        }

                        WallOrientation orientation =
                                switch (parts[1].toLowerCase()) {
                                    case "h" -> WallOrientation.HORIZONTAL;
                                    case "v" -> WallOrientation.VERTICAL;
                                    default -> throw new IllegalArgumentException(
                                            "Orientação deve ser h ou v."
                                    );
                                };

                        int row = Integer.parseInt(parts[2]);
                        int col = Integer.parseInt(parts[3]);

                        state = service.placeWall(
                                playerId,
                                new Wall(row, col, orientation)
                        );

                        System.out.println(state.message());

                    } else {
                        System.out.println("Comando desconhecido. Digite 'ajuda'.");
                    }

                } catch (Exception e) {
                    // Jogada inválida ou erro de digitação: mostra e continua.
                    System.out.println("ERRO: " + e.getMessage());
                }

            } catch (Exception e) {
                // Falha de comunicação com o servidor encerra o cliente.
                System.err.println("Falha na comunicação com o servidor: "
                        + e.getMessage());
                break;
            }
        }
    }

    /**
     * Inicia a thread vigia ("watcher"): enquanto o cliente roda, consulta
     * o servidor a cada 700 ms apenas para detectar se a partida foi
     * encerrada pela saída de outro jogador; nesse caso, avisa no console
     * e encerra o processo mesmo que o usuário esteja parado no prompt.
     */
    private void startWatcher() {
        Thread watcher = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(700);
                    if (!running.get()) {
                        return;
                    }
                    GameState state = service.getState();
                    if (state.abandoned() && disconnectNotified.compareAndSet(false, true)) {
                        running.set(false);
                        System.out.println();
                        System.out.println(state.message());
                        System.out.println("Partida encerrada.");
                        System.exit(0);
                    }
                } catch (InterruptedException e) {
                    // Thread interrompida: encerra o vigia silenciosamente.
                    return;
                } catch (Exception ignored) {
                    // Falha pontual de consulta não encerra a partida.
                }
            }
        }, "quoridor-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Avisa o servidor que este jogador está saindo, no máximo uma vez e
     * sem propagar erros (daí "quietly"): é chamada tanto na saída normal
     * (comando "sair") quanto no shutdown hook do processo.
     */
    private void leaveQuietly() {
        running.set(false);
        disconnectNotified.set(true);
        if (playerId < 0 || !left.compareAndSet(false, true)) {
            return;
        }
        try {
            service.leave(playerId);
        } catch (RemoteException ignored) {
            // Servidor já inacessível: não há mais nada a avisar.
        }
    }

    /**
     * Desenha a tela inteira do modo console: cabeçalho, situação da
     * partida (ou espera por jogadores), lista de jogadores, tabuleiro
     * em texto e o rodapé com os comandos disponíveis.
     *
     * @param state estado atual recebido do servidor
     */
    private void printState(GameState state) {
        clearScreen();

        System.out.println("==================================================");
        System.out.println("                 QUORIDOR 4P");
        System.out.println("==================================================");

        if (!state.started()) {
            System.out.println(state.message());
            System.out.println("Jogadores conectados: "
                    + state.players().size() + "/4");
            System.out.println();
            return;
        }

        System.out.println("Vez: Jogador "
                + (state.currentPlayer() + 1)
                + " - "
                + state.players().get(state.currentPlayer()).name());

        System.out.println("Mensagem: " + state.message());

        System.out.println();
        printPlayers(state);

        System.out.println();
        printBoard(state);

        System.out.println();
        System.out.println("Seu jogador: " + (playerId + 1));

        System.out.println();
        System.out.println("Comandos: m cima | m baixo | m esquerda | m direita");
        System.out.println("          m LINHA COLUNA  (pulo ou desvio ao lado)");
        System.out.println("          p h LINHA COLUNA | p v LINHA COLUNA");
        System.out.println("          estado | ajuda | sair");
    }

    /**
     * Imprime a lista de jogadores com símbolo, nome, posição do peão,
     * paredes restantes e a marca "&lt;== VEZ" ao lado de quem joga agora.
     *
     * @param state estado atual recebido do servidor
     */
    private void printPlayers(GameState state) {
        System.out.println("Jogadores:");

        for (PlayerState p : state.players()) {
            String turn = p.id() == state.currentPlayer()
                    ? " <== VEZ"
                    : "";

            System.out.printf(
                    "  %c  %-15s posição=(%d,%d) paredes=%d%s%n",
                    p.symbol(),
                    p.name(),
                    p.row(),
                    p.col(),
                    p.walls(),
                    turn
            );
        }
    }

    /**
     * Desenha o tabuleiro 9x9 em texto: '.' para casas vazias, o símbolo
     * de cada jogador na sua casa, '|' para paredes verticais e '---'
     * para paredes horizontais entre as linhas.
     *
     * @param state estado atual recebido do servidor
     */
    private void printBoard(GameState state) {
        System.out.println("Tabuleiro:");

        // Monta primeiro a grade só com casas e peões...
        char[][] board = new char[9][9];

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board[r][c] = '.';
            }
        }

        for (PlayerState p : state.players()) {
            board[p.row()][p.col()] = p.symbol();
        }

        // ...depois imprime linha a linha, intercalando as paredes.
        for (int r = 0; r < 9; r++) {
            System.out.print("    ");

            for (int c = 0; c < 9; c++) {
                System.out.print(" " + board[r][c] + " ");

                if (c < 8) {
                    System.out.print(
                            hasVerticalWall(state, r, c) ? "|" : " "
                    );
                }
            }

            System.out.println();

            if (r < 8) {
                System.out.print("    ");

                for (int c = 0; c < 9; c++) {
                    System.out.print(
                            hasHorizontalWall(state, r, c)
                                    ? "---"
                                    : "   "
                    );

                    if (c < 8) {
                        System.out.print(
                                hasHorizontalWall(state, r, c) ||
                                hasHorizontalWall(state, r, c + 1)
                                        ? "+"
                                        : " "
                        );
                    }
                }

                System.out.println();
            }
        }

        System.out.println();
        System.out.println("Legenda: 1=P1  2=P2  3=P3  4=P4");
        System.out.println("Coordenadas de paredes: linha/coluna 0..7");
    }

    /**
     * Verifica, só para o desenho em texto, se existe parede horizontal
     * cobrindo a aresta abaixo da casa (row, col) — lembrando que cada
     * parede cobre duas colunas: {@code col} e {@code col + 1} do seu canto.
     *
     * @param state estado atual recebido do servidor
     * @param row   linha da casa
     * @param col   coluna da casa
     * @return {@code true} se houver parede horizontal sob a casa
     */
    private boolean hasHorizontalWall(GameState state, int row, int col) {
        return state.walls().stream().anyMatch(w ->
                w.orientation() == WallOrientation.HORIZONTAL &&
                w.row() == row &&
                (w.col() == col || w.col() + 1 == col)
        );
    }

    /**
     * Verifica, só para o desenho em texto, se existe parede vertical
     * cobrindo a aresta à direita da casa (row, col) — lembrando que cada
     * parede cobre duas linhas: {@code row} e {@code row + 1} do seu canto.
     *
     * @param state estado atual recebido do servidor
     * @param row   linha da casa
     * @param col   coluna da casa
     * @return {@code true} se houver parede vertical à direita da casa
     */
    private boolean hasVerticalWall(GameState state, int row, int col) {
        return state.walls().stream().anyMatch(w ->
                w.orientation() == WallOrientation.VERTICAL &&
                w.col() == col &&
                (w.row() == row || w.row() + 1 == row)
        );
    }

    /**
     * Imprime o texto de ajuda do modo console, com a sintaxe dos
     * comandos de movimento e de parede e exemplos de uso.
     */
    private void printHelp() {
        System.out.println();
        System.out.println("============= AJUDA =============");
        System.out.println("Movimentos:");
        System.out.println("  m cima");
        System.out.println("  m baixo");
        System.out.println("  m esquerda");
        System.out.println("  m direita");
        System.out.println("  m LINHA COLUNA");
        System.out.println("Com uma peça à frente, o salto reto usa a mesma direção.");
        System.out.println("Se uma parede bloquear a passagem, use m LINHA COLUNA");
        System.out.println("para escolher a casa ao lado.");
        System.out.println();
        System.out.println("Paredes:");
        System.out.println("  p h LINHA COLUNA");
        System.out.println("  p v LINHA COLUNA");
        System.out.println();
        System.out.println("Exemplos:");
        System.out.println("  p h 2 3");
        System.out.println("  p v 4 6");
        System.out.println();
        System.out.println("Uma parede deve preservar pelo menos");
        System.out.println("um caminho para cada jogador.");
        System.out.println("=================================");
    }

    /**
     * Limpa a tela do terminal usando códigos de escape ANSI, para que
     * cada redesenho do estado substitua o anterior.
     */
    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
