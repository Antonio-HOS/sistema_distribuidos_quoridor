package br.uesc.quoridor.server;

import br.uesc.quoridor.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class GameServiceImpl extends UnicastRemoteObject implements GameService {

    private static final int SIZE = 9;
    private static final int MAX_PLAYERS = 4;
    private static final int WALLS_PER_PLAYER = 5;

    private final List<Player> players = new ArrayList<>();
    private final List<Wall> walls = new ArrayList<>();

    private int currentPlayer = 0;
    private Integer winner = null;
    private String message = "Aguardando 4 jogadores...";

    protected GameServiceImpl() throws RemoteException {
        super();
    }

    private static class Player {
        int id;
        String name;
        int row;
        int col;
        int walls;
        char symbol;

        Player(int id, String name, int row, int col, char symbol) {
            this.id = id;
            this.name = name;
            this.row = row;
            this.col = col;
            this.walls = WALLS_PER_PLAYER;
            this.symbol = symbol;
        }

        PlayerState snapshot() {
            return new PlayerState(id, name, row, col, walls, symbol);
        }
    }

    @Override
    public synchronized int join(String playerName) throws RemoteException {
        if (players.size() >= MAX_PLAYERS) {
            throw new RemoteException("A partida já possui 4 jogadores.");
        }

        if (playerName == null || playerName.isBlank()) {
            throw new RemoteException("Nome inválido.");
        }

        String cleanName = playerName.trim();

        for (Player p : players) {
            if (p.name.equalsIgnoreCase(cleanName)) {
                throw new RemoteException("Esse nome já está em uso.");
            }
        }

        int id = players.size();

        int[][] starts = {
                {0, 4},
                {4, 8},
                {8, 4},
                {4, 0}
        };

        char[] symbols = {'1', '2', '3', '4'};

        Player p = new Player(
                id,
                cleanName,
                starts[id][0],
                starts[id][1],
                symbols[id]
        );

        players.add(p);

        if (players.size() == MAX_PLAYERS) {
            message = "Os 4 jogadores entraram. Partida iniciada!";
        } else {
            message = "Jogador " + cleanName + " entrou. " +
                    "Faltam " + (MAX_PLAYERS - players.size()) + ".";
        }

        return id;
    }

    @Override
    public synchronized GameState getState() throws RemoteException {
        return snapshot();
    }

    @Override
    public synchronized GameState move(int playerId, Direction direction)
            throws RemoteException {

        Player player = prepareMove(playerId);
        Set<Position> destinations = legalDestinations(playerId);

        Position adjacent = step(player.row, player.col, direction);
        if (destinations.contains(adjacent)) {
            return applyMove(player, adjacent);
        }

        Position jump = step(adjacent.row(), adjacent.col(), direction);
        if (destinations.contains(jump)) {
            return applyMove(player, jump);
        }

        List<Position> sides = sideDestinations(player, direction, destinations);
        if (sides.size() == 1) {
            return applyMove(player, sides.get(0));
        }
        if (sides.size() > 1) {
            throw new RemoteException(
                    "A passagem atrás da peça está bloqueada. Escolha uma das casas ao lado."
            );
        }

        throw new RemoteException("Movimento inválido.");
    }

    @Override
    public synchronized GameState moveTo(int playerId, int row, int col)
            throws RemoteException {

        Player player = prepareMove(playerId);
        Position target = new Position(row, col);

        if (!legalDestinations(playerId).contains(target)) {
            throw new RemoteException("Movimento inválido.");
        }

        return applyMove(player, target);
    }

    @Override
    public synchronized GameState placeWall(int playerId, Wall wall)
            throws RemoteException {

        validateStarted();
        validatePlayer(playerId);
        validateTurn(playerId);

        if (winner != null) {
            throw new RemoteException("A partida já terminou.");
        }

        Player player = players.get(playerId);

        if (player.walls <= 0) {
            throw new RemoteException("Você não possui mais paredes.");
        }

        validateWallCoordinates(wall);

        if (walls.contains(wall)) {
            throw new RemoteException("Já existe uma parede nessa posição.");
        }

        if (crossesOrOverlaps(wall)) {
            throw new RemoteException("A parede cruza ou sobrepõe uma parede existente.");
        }

        walls.add(wall);

        if (!allPlayersHavePath()) {
            walls.remove(walls.size() - 1);
            throw new RemoteException(
                    "Parede inválida: ela bloquearia o caminho de algum jogador."
            );
        }

        player.walls--;
        advanceTurn();
        message = player.name + " colocou uma parede " +
                wall.orientation().name().toLowerCase() +
                " em (" + wall.row() + "," + wall.col() + ").";

        return snapshot();
    }

    @Override
    public synchronized void leave(int playerId) throws RemoteException {
        // A partida acadêmica mantém os lugares dos jogadores para preservar
        // o estado. O cliente pode encerrar sem destruir a partida.
    }

    private Player prepareMove(int playerId) throws RemoteException {
        validateStarted();
        validatePlayer(playerId);
        validateTurn(playerId);

        if (winner != null) {
            throw new RemoteException("A partida já terminou.");
        }

        return players.get(playerId);
    }

    private GameState applyMove(Player player, Position target) {
        int manhattan = Math.abs(player.row - target.row())
                + Math.abs(player.col - target.col());

        player.row = target.row();
        player.col = target.col();

        if (hasReachedGoal(player)) {
            winner = player.id;
            message = player.name + " venceu a partida!";
        } else {
            advanceTurn();
            String verb = manhattan > 1 ? "saltou para" : "moveu para";
            message = player.name + " " + verb + " ("
                    + target.row() + "," + target.col() + ").";
        }

        return snapshot();
    }

    private void validateStarted() throws RemoteException {
        if (players.size() < MAX_PLAYERS) {
            throw new RemoteException("Aguardando os 4 jogadores.");
        }
    }

    private void validatePlayer(int playerId) throws RemoteException {
        if (playerId < 0 || playerId >= players.size()) {
            throw new RemoteException("ID de jogador inválido.");
        }
    }

    private void validateTurn(int playerId) throws RemoteException {
        if (playerId != currentPlayer) {
            throw new RemoteException(
                    "Não é sua vez. Vez de " + players.get(currentPlayer).name + "."
            );
        }
    }

    private void advanceTurn() {
        currentPlayer = (currentPlayer + 1) % MAX_PLAYERS;
    }

    private void validateWallCoordinates(Wall wall) throws RemoteException {
        if (wall == null) {
            throw new RemoteException("Parede nula.");
        }

        if (wall.row() < 0 || wall.row() >= SIZE - 1 ||
                wall.col() < 0 || wall.col() >= SIZE - 1) {
            throw new RemoteException(
                    "Coordenadas da parede devem estar entre 0 e 7."
            );
        }
    }

    private boolean crossesOrOverlaps(Wall candidate) {
        for (Wall existing : walls) {
            if (candidate.orientation() == existing.orientation()) {
                if (candidate.orientation() == WallOrientation.HORIZONTAL) {
                    if (candidate.row() == existing.row() &&
                            Math.abs(candidate.col() - existing.col()) <= 1) {
                        return true;
                    }
                } else {
                    if (candidate.col() == existing.col() &&
                            Math.abs(candidate.row() - existing.row()) <= 1) {
                        return true;
                    }
                }
            } else {
                if (candidate.orientation() == WallOrientation.HORIZONTAL) {
                    if (existing.row() == candidate.row() &&
                            existing.col() == candidate.col()) {
                        return true;
                    }
                } else {
                    if (candidate.row() == existing.row() &&
                            candidate.col() == existing.col()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean allPlayersHavePath() {
        for (Player p : players) {
            if (!hasPathToGoal(p)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPathToGoal(Player player) {
        Queue<Position> queue = new ArrayDeque<>();
        Set<Position> visited = new HashSet<>();

        Position start = new Position(player.row, player.col);
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Position current = queue.poll();

            if (isGoal(player, current.row, current.col)) {
                return true;
            }

            for (Direction d : Direction.values()) {
                Position next = step(current.row, current.col, d);

                if (!inside(next.row(), next.col())) {
                    continue;
                }

                if (!blocked(current.row, current.col, next.row(), next.col())) {
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }

        return false;
    }

    private Set<Position> legalDestinations(int playerId) {
        Player player = players.get(playerId);
        Set<Position> result = new HashSet<>();

        for (Direction direction : Direction.values()) {
            Position adjacent = step(player.row, player.col, direction);

            if (!inside(adjacent.row(), adjacent.col()) ||
                    blocked(player.row, player.col, adjacent.row(), adjacent.col())) {
                continue;
            }

            if (playerAt(adjacent.row(), adjacent.col()) == -1) {
                result.add(adjacent);
                continue;
            }

            // Peça de frente: salta para a casa imediatamente atrás dela.
            Position beyond = step(adjacent.row(), adjacent.col(), direction);
            if (inside(beyond.row(), beyond.col()) &&
                    !blocked(adjacent.row(), adjacent.col(), beyond.row(), beyond.col()) &&
                    playerAt(beyond.row(), beyond.col()) == -1) {
                result.add(beyond);
                continue;
            }

            // Parede, borda ou outra peça bloqueiam o salto reto: desvia para os lados.
            if (direction == Direction.UP || direction == Direction.DOWN) {
                addSideStep(result, adjacent, Direction.LEFT);
                addSideStep(result, adjacent, Direction.RIGHT);
            } else {
                addSideStep(result, adjacent, Direction.UP);
                addSideStep(result, adjacent, Direction.DOWN);
            }
        }

        return result;
    }

    private void addSideStep(Set<Position> result, Position from, Direction direction) {
        Position side = step(from.row(), from.col(), direction);

        if (inside(side.row(), side.col()) &&
                !blocked(from.row(), from.col(), side.row(), side.col()) &&
                playerAt(side.row(), side.col()) == -1) {
            result.add(side);
        }
    }

    private List<Position> sideDestinations(Player player, Direction facing,
                                            Set<Position> destinations) {
        List<Position> sides = new ArrayList<>();
        for (Position destination : destinations) {
            int dr = destination.row() - player.row;
            int dc = destination.col() - player.col;
            boolean beside = switch (facing) {
                case UP -> dr == -1 && Math.abs(dc) == 1;
                case DOWN -> dr == 1 && Math.abs(dc) == 1;
                case LEFT -> dc == -1 && Math.abs(dr) == 1;
                case RIGHT -> dc == 1 && Math.abs(dr) == 1;
            };
            if (beside) {
                sides.add(destination);
            }
        }
        return sides;
    }

    private Position step(int row, int col, Direction direction) {
        return switch (direction) {
            case UP -> new Position(row - 1, col);
            case DOWN -> new Position(row + 1, col);
            case LEFT -> new Position(row, col - 1);
            case RIGHT -> new Position(row, col + 1);
        };
    }

    /**
     * Uma parede cobre duas arestas. O movimento entre casas vizinhas
     * fica bloqueado se qualquer uma dessas arestas estiver ocupada.
     */
    private boolean blocked(int r1, int c1, int r2, int c2) {
        if (Math.abs(r1 - r2) + Math.abs(c1 - c2) != 1) {
            return true;
        }

        if (r1 == r2) {
            int left = Math.min(c1, c2);
            for (Wall wall : walls) {
                if (wall.orientation() == WallOrientation.VERTICAL
                        && wall.col() == left
                        && (wall.row() == r1 || wall.row() + 1 == r1)) {
                    return true;
                }
            }
            return false;
        }

        int top = Math.min(r1, r2);
        for (Wall wall : walls) {
            if (wall.orientation() == WallOrientation.HORIZONTAL
                    && wall.row() == top
                    && (wall.col() == c1 || wall.col() + 1 == c1)) {
                return true;
            }
        }
        return false;
    }

    private int playerAt(int row, int col) {
        for (Player p : players) {
            if (p.row == row && p.col == col) {
                return p.id;
            }
        }
        return -1;
    }

    private boolean inside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private boolean hasReachedGoal(Player p) {
        return switch (p.id) {
            case 0 -> p.row == SIZE - 1;
            case 1 -> p.col == 0;
            case 2 -> p.row == 0;
            case 3 -> p.col == SIZE - 1;
            default -> false;
        };
    }

    private boolean isGoal(Player p, int row, int col) {
        return switch (p.id) {
            case 0 -> row == SIZE - 1;
            case 1 -> col == 0;
            case 2 -> row == 0;
            case 3 -> col == SIZE - 1;
            default -> false;
        };
    }

    private GameState snapshot() {
        List<PlayerState> playerStates = players.stream()
                .map(Player::snapshot)
                .toList();

        List<Cell> legalMoves = List.of();
        if (players.size() == MAX_PLAYERS && winner == null) {
            legalMoves = legalDestinations(currentPlayer).stream()
                    .map(position -> new Cell(position.row(), position.col()))
                    .toList();
        }

        return new GameState(
                SIZE,
                playerStates,
                List.copyOf(walls),
                currentPlayer,
                players.size() == MAX_PLAYERS,
                winner,
                message,
                legalMoves
        );
    }

    private record Position(int row, int col) {
    }
}
