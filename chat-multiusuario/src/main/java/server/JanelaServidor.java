package server;

import model.Protocolo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Janela do servidor: inicia/para o servidor, mostra o registro de eventos e quantos
 * clientes estão conectados. Execute esta classe uma vez, antes dos clientes.
 */
@SuppressWarnings({"serial", "this-escape"})
public class JanelaServidor extends JFrame {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Servidor servidor = new Servidor(this::registrar);
    private final JTextField campoPorta = new JTextField(String.valueOf(Protocolo.PORTA_PADRAO), 6);
    private final JButton botaoAlternar = new JButton("Iniciar servidor");
    private final JTextArea areaLog = new JTextArea();
    private final JLabel rotuloStatus = new JLabel("Servidor parado.");

    public JanelaServidor() {
        super("Chat Multiusuário — Servidor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topo.add(new JLabel("Porta:"));
        topo.add(campoPorta);
        topo.add(botaoAlternar);

        areaLog.setEditable(false);
        areaLog.setLineWrap(true);
        areaLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        areaLog.setMargin(new java.awt.Insets(6, 8, 6, 8));

        JPanel base = new JPanel(new BorderLayout());
        base.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        base.add(rotuloStatus, BorderLayout.WEST);

        add(topo, BorderLayout.NORTH);
        add(new JScrollPane(areaLog), BorderLayout.CENTER);
        add(base, BorderLayout.SOUTH);

        botaoAlternar.addActionListener(e -> alternar());
        getRootPane().setDefaultButton(botaoAlternar);

        // Atualiza o contador de clientes duas vezes por segundo.
        new Timer(500, e -> atualizarStatus()).start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                servidor.parar();
            }
        });

        setMinimumSize(new Dimension(420, 300));
        setSize(560, 420);
        setLocationByPlatform(true);
    }

    private void alternar() {
        if (servidor.estaRodando()) {
            servidor.parar();
            campoPorta.setEnabled(true);
            botaoAlternar.setText("Iniciar servidor");
        } else {
            int porta;
            try {
                porta = Integer.parseInt(campoPorta.getText().strip());
                if (porta < 1 || porta > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                registrar("Porta inválida: use um número entre 1 e 65535.");
                return;
            }
            try {
                servidor.iniciar(porta);
                campoPorta.setEnabled(false);
                botaoAlternar.setText("Parar servidor");
            } catch (IOException e) {
                registrar("Não foi possível iniciar na porta " + porta + ": " + e.getMessage());
            }
        }
        atualizarStatus();
    }

    private void atualizarStatus() {
        if (servidor.estaRodando()) {
            rotuloStatus.setText("Rodando na porta " + servidor.getPorta()
                    + "  ·  Clientes conectados: " + servidor.getQuantidadeClientes());
        } else {
            rotuloStatus.setText("Servidor parado.");
        }
    }

    /** Pode ser chamado de qualquer thread: a atualização da tela vai para a thread da interface. */
    private void registrar(String texto) {
        String linha = "[" + LocalTime.now().format(HORA) + "] " + texto + "\n";
        SwingUtilities.invokeLater(() -> {
            areaLog.append(linha);
            areaLog.setCaretPosition(areaLog.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // usa o visual padrão do Java
        }
        SwingUtilities.invokeLater(() -> new JanelaServidor().setVisible(true));
    }
}
