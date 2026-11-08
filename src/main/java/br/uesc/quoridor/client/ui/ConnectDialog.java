/*
 * Função deste arquivo: exibir o diálogo inicial de conexão da interface
 * gráfica, onde o jogador informa nome, host e porta do RMI Registry.
 *
 * Ele apenas coleta e valida esses três dados (nome obrigatório e porta
 * entre 1 e 65535); quem efetivamente se conecta ao servidor e entra na
 * partida é o GameFrame.launch, que lê os valores daqui depois que o
 * diálogo é confirmado.
 */
package br.uesc.quoridor.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Diálogo inicial: nome do jogador, host e porta do Registry RMI.
 */
public class ConnectDialog extends JDialog {

    /** Campo de texto do nome do jogador. */
    private final JTextField nameField = new JTextField(16);

    /** Campo de texto do host do servidor RMI (padrão "localhost"). */
    private final JTextField hostField = new JTextField("localhost", 16);

    /** Campo de texto da porta do Registry RMI (padrão "1099"). */
    private final JTextField portField = new JTextField("1099", 16);

    /** {@code true} se o usuário confirmou com "Entrar na partida". */
    private boolean confirmed;

    /**
     * Monta o diálogo modal de conexão: título, formulário com nome,
     * host e porta (pré-preenchidos com os valores padrão recebidos) e
     * os botões "Entrar na partida" e "Cancelar".
     *
     * @param owner        janela pai do diálogo (pode ser {@code null})
     * @param defaultHost  host inicial do campo (usa "localhost" se vazio)
     * @param defaultPort  porta inicial do campo
     */
    public ConnectDialog(JFrame owner, String defaultHost, int defaultPort) {
        super(owner, "Conectar — Quoridor RMI", true);
        hostField.setText(defaultHost == null || defaultHost.isBlank() ? "localhost" : defaultHost);
        portField.setText(String.valueOf(defaultPort));

        // Painel raiz com respiro interno ao redor do conteúdo.
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        root.setBackground(new Color(0xF4F6F8));

        // Cabeçalho: nome do jogo e subtítulo explicativo.
        JLabel title = new JLabel("Quoridor", SwingConstants.CENTER);
        title.setFont(new Font("Georgia", Font.BOLD, 28));
        title.setForeground(new Color(0x1A2332));

        JLabel subtitle = new JLabel("4 jogadores · Java RMI", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(0x5A6A7A));

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(title, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);

        // Formulário: uma linha (rótulo + campo) para cada dado pedido.
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4);
        gc.anchor = GridBagConstraints.WEST;

        addRow(form, gc, 0, "Nome", nameField);
        addRow(form, gc, 1, "Host", hostField);
        addRow(form, gc, 2, "Porta", portField);

        // Botão de confirmação: valida os campos antes de fechar.
        JButton connect = new JButton("Entrar na partida");
        connect.setFont(new Font("Segoe UI", Font.BOLD, 13));
        connect.addActionListener(e -> confirm());

        // Botão de cancelamento: fecha sem confirmar a conexão.
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(connect);
        buttons.add(cancel);

        root.add(header, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);

        setContentPane(root);
        // Enter dentro do diálogo aciona o botão de conectar.
        getRootPane().setDefaultButton(connect);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

    /**
     * Adiciona uma linha ao formulário: o rótulo na primeira coluna e o
     * campo de texto (que se estica horizontalmente) na segunda.
     *
     * @param form  painel do formulário
     * @param gc    restrições de layout reutilizadas entre as linhas
     * @param row   índice da linha (0, 1, 2...)
     * @param label texto do rótulo
     * @param field campo de texto da linha
     */
    private void addRow(JPanel form, GridBagConstraints gc, int row, String label, JTextField field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        form.add(l, gc);

        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        form.add(field, gc);
        gc.weightx = 0;
    }

    /**
     * Valida os campos ao clicar em "Entrar na partida": nome não pode
     * ser vazio e a porta deve ser um número entre 1 e 65535. Se algo
     * estiver errado, mostra um aviso e mantém o diálogo aberto; se
     * estiver tudo certo, marca como confirmado e fecha o diálogo.
     */
    private void confirm() {
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Informe o nome do jogador.",
                    "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Porta inválida.",
                    "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }
        confirmed = true;
        dispose();
    }

    /**
     * Informa se o usuário confirmou a conexão (em vez de cancelar).
     *
     * @return {@code true} se clicou em "Entrar na partida" com dados válidos
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Devolve o nome digitado, sem espaços nas pontas.
     *
     * @return nome do jogador
     */
    public String getPlayerName() {
        return nameField.getText().trim();
    }

    /**
     * Devolve o host digitado, ou "localhost" se o campo foi deixado vazio.
     *
     * @return host do servidor RMI
     */
    public String getHost() {
        String h = hostField.getText().trim();
        return h.isEmpty() ? "localhost" : h;
    }

    /**
     * Devolve a porta digitada já convertida para número.
     * Só deve ser chamado após a confirmação (que valida o valor).
     *
     * @return porta do Registry RMI (1..65535)
     */
    public int getPort() {
        return Integer.parseInt(portField.getText().trim());
    }
}
