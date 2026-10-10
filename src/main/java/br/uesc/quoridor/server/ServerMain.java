package br.uesc.quoridor.server;

import br.uesc.quoridor.common.GameService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class ServerMain {

    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname",
                    args.length > 0 ? args[0] : "localhost");

            Registry registry = LocateRegistry.createRegistry(1099);

            GameService service = new GameServiceImpl();

            registry.rebind("Quoridor", service);

            System.out.println("==========================================");
            System.out.println("      QUORIDOR - SERVIDOR RMI");
            System.out.println("==========================================");
            System.out.println("RMI Registry: porta 1099");
            System.out.println("Serviço: Quoridor");
            System.out.println("Aguardando 4 jogadores...");
            System.out.println("==========================================");

        } catch (Exception e) {
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
