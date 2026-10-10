package br.uesc.quoridor.client;

import br.uesc.quoridor.client.ui.GameFrame;
import br.uesc.quoridor.common.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Cliente Quoridor.
 * Por padrão abre a interface gráfica Swing.
 * Use {@code --console} para o cliente em texto.
 */
public class ClientMain {

    private final GameService service;
    private final Scanner scanner;
    private int playerId = -1;

    public ClientMain(GameService service) {
        this.service = service;
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
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

    private static void runConsole(String[] args) {
        try {
            Scanner input = new Scanner(System.in);

            System.out.print("Nome do jogador: ");
            String name = input.nextLine().trim();

            String host;
            int port;

            if (args.length >= 1) {
                host = args[0];
            } else {
                System.out.print("Host do servidor RMI [localhost]: ");
                host = input.nextLine().trim();
                if (host.isBlank()) {
                    host = "localhost";
                }
            }

            if (args.length >= 2) {
                port = Integer.parseInt(args[1]);
            } else {
                System.out.print("Porta do Registry [1099]: ");
                String value = input.nextLine().trim();
                port = value.isBlank() ? 1099 : Integer.parseInt(value);
            }

            Registry registry = LocateRegistry.getRegistry(host, port);
            GameService service = (GameService) registry.lookup("Quoridor");

            ClientMain client = new ClientMain(service);
            client.playerId = service.join(name);

            System.out.println();
            System.out.println("Você é o jogador " + (client.playerId + 1) + ".");
            System.out.println("Aguardando os outros jogadores...");
            client.loop();

        } catch (Exception e) {
            System.err.println("Erro no cliente: " + e.getMessage());
        }
    }

    private void loop() {
        while (true) {
            try {
                GameState state = service.getState();
                printState(state);

                if (state.winner() != null) {
                    System.out.println("Partida encerrada.");
                    break;
                }

                System.out.println();
                System.out.print("Comando > ");

                String command = scanner.nextLine().trim();

                if (command.equalsIgnoreCase("sair")) {
                    service.leave(playerId);
                    break;
                }

                if (command.equalsIgnoreCase("ajuda")) {
                    printHelp();
                    continue;
                }

                if (command.equalsIgnoreCase("estado")) {
                    continue;
                }

                try {
                    if (command.toLowerCase().startsWith("m ")) {
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
                    System.out.println("ERRO: " + e.getMessage());
                }

            } catch (Exception e) {
                System.err.println("Falha na comunicação com o servidor: "
                        + e.getMessage());
                break;
            }
        }
    }

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

    private void printBoard(GameState state) {
        System.out.println("Tabuleiro:");

        char[][] board = new char[9][9];

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board[r][c] = '.';
            }
        }

        for (PlayerState p : state.players()) {
            board[p.row()][p.col()] = p.symbol();
        }

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

    private boolean hasHorizontalWall(GameState state, int row, int col) {
        return state.walls().stream().anyMatch(w ->
                w.orientation() == WallOrientation.HORIZONTAL &&
                w.row() == row &&
                (w.col() == col || w.col() + 1 == col)
        );
    }

    private boolean hasVerticalWall(GameState state, int row, int col) {
        return state.walls().stream().anyMatch(w ->
                w.orientation() == WallOrientation.VERTICAL &&
                w.col() == col &&
                (w.row() == row || w.row() + 1 == row)
        );
    }

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

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
