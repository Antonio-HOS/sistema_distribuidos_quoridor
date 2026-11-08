/*
 * Função deste arquivo: definir a interface remota GameService, que é o
 * contrato RMI entre clientes e servidor do Quoridor.
 *
 * Esta interface é o coração da arquitetura distribuída: o servidor
 * publica uma implementação dela (GameServiceImpl) no RMI Registry com
 * o nome "Quoridor", e cada cliente obtém um stub via lookup e passa a
 * invocar estes métodos remotamente, como se fossem chamadas locais.
 */
package br.uesc.quoridor.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interface remota do jogo Quoridor.
 *
 * <p>Todos os métodos podem lançar {@link RemoteException}, exigência do
 * Java RMI para sinalizar falhas de comunicação ou erros de validação
 * ocorridos no servidor.</p>
 */
public interface GameService extends Remote {

    /**
     * Registra um jogador na partida.
     *
     * @param playerName nome do jogador (não pode ser vazio nem repetido)
     * @return o ID atribuído ao jogador (0..3), usado em todas as demais chamadas
     * @throws RemoteException se a partida já tiver 4 jogadores, já tiver
     *                         sido encerrada ou o nome for inválido/repetido
     */
    int join(String playerName) throws RemoteException;

    /**
     * Obtém uma fotografia (snapshot) atual e serializável do estado da partida.
     * É o método usado pelos clientes para atualizar a tela (polling).
     *
     * @return o estado atual do jogo
     * @throws RemoteException em caso de falha de comunicação com o servidor
     */
    GameState getState() throws RemoteException;

    /**
     * Move o peão do jogador um passo (ou um salto/desvio, conforme as
     * regras) na direção indicada.
     *
     * @param playerId  ID do jogador que está jogando (deve ser o da vez)
     * @param direction direção do movimento
     * @return o novo estado do jogo após o movimento
     * @throws RemoteException se não for a vez do jogador, se o movimento
     *                         for inválido ou se a partida não estiver em andamento
     */
    GameState move(int playerId, Direction direction) throws RemoteException;

    /**
     * Move o peão do jogador diretamente para a casa de destino indicada.
     * É a forma usada pela interface gráfica, na qual o usuário clica na
     * casa desejada (inclui saltos e desvios laterais).
     *
     * @param playerId ID do jogador que está jogando (deve ser o da vez)
     * @param row      linha de destino (0..8)
     * @param col      coluna de destino (0..8)
     * @return o novo estado do jogo após o movimento
     * @throws RemoteException se o destino não for um movimento legal
     */
    GameState moveTo(int playerId, int row, int col) throws RemoteException;

    /**
     * Coloca uma parede no tabuleiro em nome do jogador.
     *
     * @param playerId ID do jogador que está jogando (deve ser o da vez)
     * @param wall     parede a colocar (posição 0..7 e orientação)
     * @return o novo estado do jogo após a colocação da parede
     * @throws RemoteException se o jogador não tiver paredes, se a posição
     *                         for inválida, se a parede cruzar/sobrepor outra
     *                         ou se ela bloquear o caminho de algum jogador
     */
    GameState placeWall(int playerId, Wall wall) throws RemoteException;

    /**
     * Avisa o servidor que o jogador está saindo da partida.
     * Pela regra do jogo, a saída de qualquer jogador encerra a partida
     * para todos os demais.
     *
     * @param playerId ID do jogador que está saindo
     * @throws RemoteException se o ID for inválido ou houver falha de comunicação
     */
    void leave(int playerId) throws RemoteException;
}
