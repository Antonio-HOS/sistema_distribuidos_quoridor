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

    private final JTextField nameField = new JTextField(16);
    private final JTextField hostField = new JTextField("localhost", 16);
    private final JTextField portField = new JTextField("1099", 16);
    private boolean confirmed;

    public ConnectDialog(JFrame owner, String defaultHost, int defaultPort) {
        super(owner, "Conectar — Quoridor RMI", true);
        hostField.setText(defaultHost == null || defaultHost.isBlank() ? "localhost" : defaultHost);
        portField.setText(String.valueOf(defaultPort));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        root.setBackground(new Color(0xF4F6F8));

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

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4);
        gc.anchor = GridBagConstraints.WEST;

        addRow(form, gc, 0, "Nome", nameField);
        addRow(form, gc, 1, "Host", hostField);
        addRow(form, gc, 2, "Porta", portField);

        JButton connect = new JButton("Entrar na partida");
        connect.setFont(new Font("Segoe UI", Font.BOLD, 13));
        connect.addActionListener(e -> confirm());

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
        getRootPane().setDefaultButton(connect);
        pack();
        setLocationRelativeTo(owner);
        setResizable(false);
    }

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

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getPlayerName() {
        return nameField.getText().trim();
    }

    public String getHost() {
        String h = hostField.getText().trim();
        return h.isEmpty() ? "localhost" : h;
    }

    public int getPort() {
        return Integer.parseInt(portField.getText().trim());
    }
}
