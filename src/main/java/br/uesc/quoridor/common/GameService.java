package br.uesc.quoridor.common;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GameService extends Remote {

    int join(String playerName) throws RemoteException;

    GameState getState() throws RemoteException;

    GameState move(int playerId, Direction direction) throws RemoteException;

    GameState moveTo(int playerId, int row, int col) throws RemoteException;

    GameState placeWall(int playerId, Wall wall) throws RemoteException;

    void leave(int playerId) throws RemoteException;
}
