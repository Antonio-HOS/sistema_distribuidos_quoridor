/*
 * Função deste arquivo: implementar o objeto remoto do servidor, ou seja,
 * toda a lógica e as regras do Quoridor para 4 jogadores.
 *
 * Esta é a classe "autoridade" do sistema distribuído: ela guarda o estado
 * oficial da partida (jogadores, paredes, vez, vencedor), valida todas as
 * jogadas recebidas via RMI (movimentos, saltos, desvios e paredes, incluindo
 * a regra de nunca bloquear totalmente o caminho de um jogador) e devolve
 * aos clientes apenas fotografias serializáveis do estado (GameState).
 * Os clientes nunca decidem regra nenhuma: apenas pedem e exibem.
 */
package br.uesc.quoridor.server;

import br.uesc.quoridor.common.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Implementação remota de {@link GameService}.
 *
 * <p>Todos os métodos públicos são {@code synchronized} porque o RMI pode
 * atender chamadas de vários clientes ao mesmo tempo em threads diferentes;
 * a sincronização garante que o estado da partida nunca seja alterado por
 * duas jogadas simultâneas.</p>
 */
public class GameServiceImpl extends UnicastRemoteObject implements GameService {

    /** Tamanho do tabuleiro: 9 casas por lado. */
    private static final int SIZE = 9;

    /** Número de jogadores necessário (e máximo) para uma partida. */
    private static final int MAX_PLAYERS = 4;

    /** Quantidade de paredes com que cada jogador começa a partida. */
    private static final int WALLS_PER_PLAYER = 5;

    /** Jogadores conectados, em ordem de entrada (o índice é o ID). */
    private final List<Player> players = new ArrayList<>();

    /** Paredes já colocadas no tabuleiro. */
    private final List<Wall> walls = new ArrayList<>();

    /** ID do jogador da vez (0..3). */
    private int currentPlayer = 0;

    /** ID do vencedor; {@code null} enquanto a partida não tem vencedor. */
    private Integer winner = null;

    /** {@code true} se a partida foi encerrada por desconexão de um jogador. */
    private boolean abandoned = false;

    /** Mensagem textual que descreve o último evento da partida. */
    private String message = "Aguardando 4 jogadores...";

    /**
     * Cria o objeto remoto. A chamada a {@code super()} (de
     * {@link UnicastRemoteObject}) é o que exporta o objeto, deixando-o
     * apto a receber chamadas remotas via RMI.
     *
     * @throws RemoteException se a exportação RMI do objeto falhar
     */
    protected GameServiceImpl() throws RemoteException {
        super();
    }

    /**
     * Função desta classe interna: representar, apenas dentro do servidor,
     * o estado mutável de um jogador (posição atual do peão, paredes
     * restantes etc.). Os clientes nunca veem esta classe; para eles o
     * servidor envia apenas o {@link PlayerState} gerado por
     * {@link Player#snapshot()}.
     */
    private static class Player {
        /** ID do jogador (0..3), igual ao seu índice na lista de jogadores. */
        int id;
        /** Nome do jogador. */
        String name;
        /** Linha atual do peão (0..8). */
        int row;
        /** Coluna atual do peão (0..8). */
        int col;
        /** Paredes que o jogador ainda pode colocar. */
        int walls;
        /** Símbolo do jogador nos tabuleiros ('1' a '4'). */
        char symbol;

        /**
         * Cria um jogador já posicionado na sua casa inicial.
         *
         * @param id     ID do jogador (0..3)
         * @param name   nome do jogador
         * @param row    linha inicial do peão
         * @param col    coluna inicial do peão
         * @param symbol símbolo do jogador ('1' a '4')
         */
        Player(int id, String name, int row, int col, char symbol) {
            this.id = id;
            this.name = name;
            this.row = row;
            this.col = col;
            this.walls = WALLS_PER_PLAYER;
            this.symbol = symbol;
        }

        /**
         * Gera a versão serializável e imutável deste jogador, que é a
         * que pode ser enviada aos clientes dentro do {@link GameState}.
         *
         * @return fotografia atual do jogador
         */
        PlayerState snapshot() {
            return new PlayerState(id, name, row, col, walls, symbol);
        }
    }

    /**
     * Registra um novo jogador na partida, atribuindo-lhe ID, casa inicial
     * (meio de um dos lados do tabuleiro) e símbolo, conforme a ordem de
     * entrada. Quando o 4º jogador entra, a partida é considerada iniciada.
     *
     * @param playerName nome do jogador
     * @return ID atribuído ao jogador (0..3)
     * @throws RemoteException se a partida já foi encerrada, já tem 4
     *                         jogadores ou o nome é vazio/repetido
     */
    @Override
    public synchronized int join(String playerName) throws RemoteException {
        // Partida encerrada por desconexão não aceita mais ninguém.
        if (abandoned) {
            throw new RemoteException("A partida foi encerrada porque um jogador se desconectou.");
        }
        if (players.size() >= MAX_PLAYERS) {
            throw new RemoteException("A partida já possui 4 jogadores.");
        }

        if (playerName == null || playerName.isBlank()) {
            throw new RemoteException("Nome inválido.");
        }

        String cleanName = playerName.trim();

        // Nomes devem ser únicos para os jogadores se identificarem.
        for (Player p : players) {
            if (p.name.equalsIgnoreCase(cleanName)) {
                throw new RemoteException("Esse nome já está em uso.");
            }
        }

        int id = players.size();

        // Casas iniciais: meio de cada lado do tabuleiro.
        // P1 no topo, P2 na direita, P3 embaixo e P4 na esquerda.
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

    /**
     * Devolve a fotografia atual da partida. É o método chamado
     * repetidamente (polling) pelos clientes para atualizar a tela.
     *
     * @return estado atual do jogo
     * @throws RemoteException em caso de falha de comunicação
     */
    @Override
    public synchronized GameState getState() throws RemoteException {
        return snapshot();
    }

    /**
     * Move o peão na direção pedida, interpretando a direção conforme as
     * regras: passo simples para a casa vizinha; salto reto sobre um peão
     * adversário; ou desvio lateral quando o salto reto está bloqueado
     * (se houver dois lados possíveis, a jogada é ambígua e é rejeitada,
     * pois por direção não dá para saber qual lado o jogador quer).
     *
     * @param playerId  ID de quem joga (deve ser o jogador da vez)
     * @param direction direção desejada
     * @return novo estado da partida após o movimento
     * @throws RemoteException se o movimento for inválido ou ambíguo
     */
    @Override
    public synchronized GameState move(int playerId, Direction direction)
            throws RemoteException {

        Player player = prepareMove(playerId);
        Set<Position> destinations = legalDestinations(playerId);

        // 1ª tentativa: a casa vizinha na direção pedida (passo simples
        // ou desvio já calculado na lista de destinos legais).
        Position adjacent = step(player.row, player.col, direction);
        if (destinations.contains(adjacent)) {
            return applyMove(player, adjacent);
        }

        // 2ª tentativa: a casa atrás do peão adversário (salto reto).
        Position jump = step(adjacent.row(), adjacent.col(), direction);
        if (destinations.contains(jump)) {
            return applyMove(player, jump);
        }

        // 3ª tentativa: desvios laterais ao redor do peão adversário,
        // usados quando o salto reto está bloqueado por parede/borda.
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

    /**
     * Move o peão diretamente para a casa indicada, desde que ela esteja
     * na lista de destinos legais (passo, salto ou desvio). É o método
     * usado quando o usuário clica numa casa na interface gráfica.
     *
     * @param playerId ID de quem joga (deve ser o jogador da vez)
     * @param row      linha de destino (0..8)
     * @param col      coluna de destino (0..8)
     * @return novo estado da partida após o movimento
     * @throws RemoteException se o destino não for um movimento legal
     */
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

    /**
     * Coloca uma parede no tabuleiro, validando: partida em andamento,
     * vez do jogador, paredes disponíveis, coordenadas 0..7, ausência de
     * duplicidade/cruzamento/sobreposição e a regra de ouro do Quoridor —
     * a parede não pode deixar nenhum jogador sem caminho até o objetivo
     * (verificação feita removendo a parede e rejeitando a jogada caso
     * algum caminho desapareça).
     *
     * @param playerId ID de quem joga (deve ser o jogador da vez)
     * @param wall     parede a colocar
     * @return novo estado da partida após a colocação
     * @throws RemoteException se qualquer validação acima falhar
     */
    @Override
    public synchronized GameState placeWall(int playerId, Wall wall)
            throws RemoteException {

        rejectIfOver();
        validateStarted();
        validatePlayer(playerId);
        validateTurn(playerId);

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

        // Coloca a parede provisoriamente para testar os caminhos.
        walls.add(wall);

        if (!allPlayersHavePath()) {
            // Desfaz a colocação provisória e rejeita a jogada.
            walls.remove(walls.size() - 1);
            throw new RemoteException(
                    "Parede inválida: ela bloquearia o caminho de algum jogador."
            );
        }

        // Jogada confirmada: consome uma parede do jogador e passa a vez.
        player.walls--;
        advanceTurn();
        message = player.name + " colocou uma parede " +
                wall.orientation().name().toLowerCase() +
                " em (" + wall.row() + "," + wall.col() + ").";

        return snapshot();
    }

    /**
     * Registra a saída de um jogador. Pela regra adotada, qualquer saída
     * encerra a partida para todos (a partida passa a constar como
     * abandonada e a mensagem guarda quem se desconectou).
     *
     * @param playerId ID do jogador que saiu
     * @throws RemoteException se o ID for inválido
     */
    @Override
    public synchronized void leave(int playerId) throws RemoteException {
        if (playerId < 0 || playerId >= players.size()) {
            throw new RemoteException("ID de jogador inválido.");
        }
        // Se a partida já acabou (vitória ou outra saída), não há o que fazer.
        if (winner != null || abandoned) {
            return;
        }
        abandoned = true;
        message = players.get(playerId).name + " se desconectou. Partida encerrada.";
    }

    /**
     * Executa as validações comuns a qualquer tentativa de movimento
     * (partida não encerrada, partida iniciada, ID válido e vez do
     * jogador) e devolve o jogador já localizado.
     *
     * @param playerId ID de quem quer mover
     * @return o jogador correspondente ao ID
     * @throws RemoteException se alguma validação falhar
     */
    private Player prepareMove(int playerId) throws RemoteException {
        rejectIfOver();
        validateStarted();
        validatePlayer(playerId);
        validateTurn(playerId);
        return players.get(playerId);
    }

    /**
     * Rejeita jogadas quando a partida já terminou, seja por vitória de
     * alguém, seja por desconexão de um jogador.
     *
     * @throws RemoteException com a explicação do encerramento
     */
    private void rejectIfOver() throws RemoteException {
        if (abandoned) {
            throw new RemoteException(message);
        }
        if (winner != null) {
            throw new RemoteException("A partida já terminou.");
        }
    }

    /**
     * Efetivamente desloca o peão para a casa de destino, verifica se ele
     * alcançou o lado oposto (vitória) e, caso contrário, passa a vez e
     * registra a mensagem da jogada (distinguindo "moveu" de "saltou"
     * pela distância percorrida).
     *
     * @param player jogador que está se movendo
     * @param target casa de destino já validada
     * @return novo estado da partida após o movimento
     */
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

    /**
     * Garante que a partida já começou, isto é, que os 4 jogadores entraram.
     *
     * @throws RemoteException enquanto houver menos de 4 jogadores
     */
    private void validateStarted() throws RemoteException {
        if (players.size() < MAX_PLAYERS) {
            throw new RemoteException("Aguardando os 4 jogadores.");
        }
    }

    /**
     * Garante que o ID informado corresponde a um jogador existente.
     *
     * @param playerId ID a validar
     * @throws RemoteException se o ID estiver fora da lista de jogadores
     */
    private void validatePlayer(int playerId) throws RemoteException {
        if (playerId < 0 || playerId >= players.size()) {
            throw new RemoteException("ID de jogador inválido.");
        }
    }

    /**
     * Garante que quem está jogando é o jogador da vez.
     *
     * @param playerId ID de quem tentou jogar
     * @throws RemoteException se não for a vez desse jogador
     */
    private void validateTurn(int playerId) throws RemoteException {
        if (playerId != currentPlayer) {
            throw new RemoteException(
                    "Não é sua vez. Vez de " + players.get(currentPlayer).name + "."
            );
        }
    }

    /**
     * Passa a vez para o próximo jogador, em rodízio (0 → 1 → 2 → 3 → 0...).
     */
    private void advanceTurn() {
        currentPlayer = (currentPlayer + 1) % MAX_PLAYERS;
    }

    /**
     * Valida a existência e as coordenadas de uma parede: o canto superior
     * esquerdo deve estar entre 0 e 7 em linha e coluna, pois a parede
     * sempre ocupa duas casas de comprimento.
     *
     * @param wall parede a validar
     * @throws RemoteException se a parede for nula ou estiver fora do intervalo
     */
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

    /**
     * Verifica se a parede candidata cruza ou se sobrepõe a alguma parede
     * já colocada. Duas paredes da mesma orientação se sobrepõem se
     * estiverem na mesma linha/coluna e a uma distância de até 1; paredes
     * de orientações opostas se cruzam se tiverem o mesmo canto.
     *
     * @param candidate parede que se deseja colocar
     * @return {@code true} se houver cruzamento ou sobreposição
     */
    private boolean crossesOrOverlaps(Wall candidate) {
        for (Wall existing : walls) {
            if (candidate.orientation() == existing.orientation()) {
                // Mesma orientação: sobreposição total ou parcial.
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
                // Orientações opostas: cruzamento no canto comum.
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

    /**
     * Verifica se todos os jogadores ainda têm pelo menos um caminho até
     * o seu objetivo com as paredes atuais do tabuleiro.
     *
     * @return {@code true} se nenhum jogador ficou totalmente bloqueado
     */
    private boolean allPlayersHavePath() {
        for (Player p : players) {
            if (!hasPathToGoal(p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Busca em largura (BFS) a partir da casa do jogador, atravessando
     * apenas arestas não bloqueadas por paredes, para saber se o lado
     * objetivo dele é alcançável. Peões de outros jogadores são ignorados
     * aqui: eles se movem, portanto não podem bloquear um caminho para sempre.
     *
     * @param player jogador cujo caminho será testado
     * @return {@code true} se existe caminho até o objetivo
     */
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

            // Expande para os 4 vizinhos ortogonais não bloqueados.
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

    /**
     * Calcula todas as casas para as quais o jogador pode mover o peão
     * neste turno, aplicando as regras de movimento do Quoridor: passo
     * simples para casa vizinha livre; salto reto sobre um peão vizinho;
     * e, quando o salto reto é bloqueado por parede, borda ou outro peão,
     * desvios para as casas laterais do peão vizinho.
     *
     * @param playerId ID do jogador
     * @return conjunto de casas de destino legais (sem repetições)
     */
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
                // Casa vizinha livre: passo simples.
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

    /**
     * Adiciona ao conjunto de destinos a casa lateral de um peão vizinho,
     * se ela estiver dentro do tabuleiro, livre de peões e sem parede
     * bloqueando a passagem (parte da regra do desvio ao redor de um peão).
     *
     * @param result    conjunto de destinos legais sendo montado
     * @param from      casa do peão vizinho que está sendo contornado
     * @param direction direção lateral a tentar (perpendicular ao movimento)
     */
    private void addSideStep(Set<Position> result, Position from, Direction direction) {
        Position side = step(from.row(), from.col(), direction);

        if (inside(side.row(), side.col()) &&
                !blocked(from.row(), from.col(), side.row(), side.col()) &&
                playerAt(side.row(), side.col()) == -1) {
            result.add(side);
        }
    }

    /**
     * Filtra, dentre os destinos legais, aqueles que são desvios laterais
     * em relação à direção que o jogador tentou seguir (casas diagonais ao
     * jogador, ao lado do peão que ele queria saltar). Usado por
     * {@link #move(int, Direction)} para resolver movimentos por direção.
     *
     * @param player       jogador que está se movendo
     * @param facing       direção que o jogador tentou seguir
     * @param destinations conjunto de destinos legais do jogador
     * @return lista de destinos que são desvios laterais naquela direção
     */
    private List<Position> sideDestinations(Player player, Direction facing,
                                            Set<Position> destinations) {
        List<Position> sides = new ArrayList<>();
        for (Position destination : destinations) {
            int dr = destination.row() - player.row;
            int dc = destination.col() - player.col;
            // Casa "ao lado" do peão adversário: 1 passo na direção
            // tentada e 1 passo para um dos lados.
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

    /**
     * Calcula a casa vizinha de uma posição em uma direção, sem validar
     * limites do tabuleiro (quem chama deve conferir com {@link #inside}).
     *
     * @param row       linha de partida
     * @param col       coluna de partida
     * @param direction direção do passo
     * @return a posição vizinha na direção indicada
     */
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
     *
     * @param r1 linha da casa de origem
     * @param c1 coluna da casa de origem
     * @param r2 linha da casa de destino
     * @param c2 coluna da casa de destino
     * @return {@code true} se as casas não forem vizinhas ortogonais ou se
     *         uma parede bloquear a passagem entre elas
     */
    private boolean blocked(int r1, int c1, int r2, int c2) {
        if (Math.abs(r1 - r2) + Math.abs(c1 - c2) != 1) {
            return true;
        }

        if (r1 == r2) {
            // Movimento horizontal: quem bloqueia é uma parede vertical
            // na aresta entre as duas colunas.
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

        // Movimento vertical: quem bloqueia é uma parede horizontal
        // na aresta entre as duas linhas.
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

    /**
     * Informa qual jogador ocupa uma casa, se houver algum.
     *
     * @param row linha da casa
     * @param col coluna da casa
     * @return ID do jogador na casa, ou {@code -1} se a casa estiver livre
     */
    private int playerAt(int row, int col) {
        for (Player p : players) {
            if (p.row == row && p.col == col) {
                return p.id;
            }
        }
        return -1;
    }

    /**
     * Verifica se uma posição está dentro do tabuleiro 9x9.
     *
     * @param row linha a verificar
     * @param col coluna a verificar
     * @return {@code true} se a posição existir no tabuleiro
     */
    private boolean inside(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    /**
     * Verifica se o peão do jogador já está sobre o seu lado objetivo
     * (condição de vitória, considerando a posição atual dele).
     *
     * @param p jogador a verificar
     * @return {@code true} se o jogador venceu
     */
    private boolean hasReachedGoal(Player p) {
        return switch (p.id) {
            case 0 -> p.row == SIZE - 1; // P1: do topo até a linha de baixo
            case 1 -> p.col == 0;        // P2: da direita até a coluna da esquerda
            case 2 -> p.row == 0;        // P3: de baixo até a linha do topo
            case 3 -> p.col == SIZE - 1; // P4: da esquerda até a coluna da direita
            default -> false;
        };
    }

    /**
     * Verifica se uma casa qualquer pertence ao lado objetivo do jogador.
     * Diferente de {@link #hasReachedGoal(Player)}, recebe a casa como
     * parâmetro porque é usado durante a busca de caminho (BFS).
     *
     * @param p   jogador dono do objetivo
     * @param row linha da casa a verificar
     * @param col coluna da casa a verificar
     * @return {@code true} se a casa estiver no lado objetivo do jogador
     */
    private boolean isGoal(Player p, int row, int col) {
        return switch (p.id) {
            case 0 -> row == SIZE - 1;
            case 1 -> col == 0;
            case 2 -> row == 0;
            case 3 -> col == SIZE - 1;
            default -> false;
        };
    }

    /**
     * Monta a fotografia serializável e imutável da partida que será
     * enviada aos clientes, incluindo os jogadores, as paredes, a vez,
     * o vencedor (se houver) e os movimentos legais do jogador da vez
     * (para a interface gráfica poder destacar as casas clicáveis).
     *
     * @return estado atual da partida pronto para trafegar via RMI
     */
    private GameState snapshot() {
        List<PlayerState> playerStates = players.stream()
                .map(Player::snapshot)
                .toList();

        // Movimentos legais só fazem sentido com a partida em andamento.
        List<Cell> legalMoves = List.of();
        if (players.size() == MAX_PLAYERS && winner == null && !abandoned) {
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
                legalMoves,
                abandoned
        );
    }

    /**
     * Função deste record interno: representar uma posição (linha, coluna)
     * apenas dentro do servidor, para cálculos de movimento, salto, desvio
     * e busca de caminho. É o equivalente interno e mutável em uso ao
     * record {@link Cell}, que é a versão que trafega para os clientes.
     *
     * @param row linha da posição
     * @param col coluna da posição
     */
    private record Position(int row, int col) {
        // Record: o compilador gera row(), col(), equals(), hashCode()
        // e toString(); equals()/hashCode() são essenciais para o uso
        // de Position em Set e como chave de visitados na BFS.
    }
}
