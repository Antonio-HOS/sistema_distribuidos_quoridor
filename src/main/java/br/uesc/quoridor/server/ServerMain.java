/*
 * Função deste arquivo: ser o ponto de entrada (main) do processo servidor.
 *
 * Ele sobe a infraestrutura RMI do lado do servidor: cria o RMI Registry
 * na porta 1099, instancia o objeto remoto GameServiceImpl e o publica
 * no Registry com o nome "Quoridor", ficando então aguardando as
 * conexões dos 4 processos clientes.
 */
package br.uesc.quoridor.server;

import br.uesc.quoridor.common.GameService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Classe executável do servidor Quoridor.
 */
public class ServerMain {

    /**
     * Inicia o servidor RMI do jogo.
     *
     * @param args args[0] (opcional) é o hostname/IP anunciado pelo servidor
     *             RMI aos clientes; se omitido, usa "localhost"
     */
    public static void main(String[] args) {
        try {
            // Define o hostname que o RMI informará aos clientes para que
            // eles saibam para onde se conectar de volta ao servidor.
            System.setProperty("java.rmi.server.hostname",
                    args.length > 0 ? args[0] : "localhost");

            // Cria o RMI Registry local na porta padrão 1099.
            Registry registry = LocateRegistry.createRegistry(1099);

            // Instancia o objeto remoto que implementa as regras do jogo.
            GameService service = new GameServiceImpl();

            // Publica o objeto remoto no Registry com o nome "Quoridor",
            // que é o nome que os clientes procuram via lookup.
            registry.rebind("Quoridor", service);

            // A partir daqui o processo fica vivo apenas servindo chamadas
            // remotas (as threads do RMI mantêm a JVM em execução).
            System.out.println("==========================================");
            System.out.println("      QUORIDOR - SERVIDOR RMI");
            System.out.println("==========================================");
            System.out.println("RMI Registry: porta 1099");
            System.out.println("Serviço: Quoridor");
            System.out.println("Aguardando 4 jogadores...");
            System.out.println("==========================================");

        } catch (Exception e) {
            // Qualquer falha na inicialização (porta ocupada, etc.) é
            // reportada no console e encerra a tentativa de subida.
            System.err.println("Erro ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
