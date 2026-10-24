package client;

import model.Mensagem;
import model.Protocolo;
import model.Usuario;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * Janela do cliente: primeiro uma tela de entrada (servidor, porta e nome),
 * depois a tela de conversa. Execute esta classe uma vez para cada participante.
 */
@SuppressWarnings({"serial", "this-escape"})
public class JanelaChat extends JFrame implements ClienteChat.Ouvinte {

    private static final String TELA_ENTRADA = "entrada";
    private static final String TELA_CHAT = "chat";

    private final CardLayout cartoes = new CardLayout();
    private final JPanel raiz = new JPanel(cartoes);

    // Tela de entrada
    private final JTextField campoServidor = new JTextField("localhost", 14);
    private final JTextField campoPorta = new JTextField(String.valueOf(Protocolo.PORTA_PADRAO), 14);
    private final JTextField campoNome = new JTextField(14);
    private final JButton botaoEntrar = new JButton("Entrar no chat");
    private final JLabel rotuloErro = new JLabel(" ");

    // Tela de conversa
    private final JLabel rotuloTitulo = new JLabel(" ");
    private final JTextArea areaMensagens = new JTextArea();
    private final JTextField campoMensagem = new JTextField();
    private final JButton botaoEnviar = new JButton("Enviar");
    private final JButton botaoSair = new JButton("Sair");

    private ClienteChat cliente;

    public JanelaChat() {
        super("Chat Multiusuário");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        raiz.add(criarTelaEntrada(), TELA_ENTRADA);
        raiz.add(criarTelaChat(), TELA_CHAT);
        setContentPane(raiz);
        setMinimumSize(new Dimension(420, 380));
        setSize(520, 520);
        setLocationByPlatform(true);
        mostrarEntrada();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (cliente != null) {
                    cliente.desconectar();
                }
            }
        });
    }

    // ---------------------------------------------------------------- telas

    private JPanel criarTelaEntrada() {
        JPanel painel = new JPanel(new GridBagLayout());
        painel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;

        JLabel titulo = new JLabel("Chat Multiusuário");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 22f));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.insets = new Insets(6, 6, 18, 6);
        painel.add(titulo, c);

        c.gridwidth = 1;
        c.insets = new Insets(6, 6, 6, 6);
        adicionarLinha(painel, c, 1, "Servidor:", campoServidor);
        adicionarLinha(painel, c, 2, "Porta:", campoPorta);
        adicionarLinha(painel, c, 3, "Seu nome:", campoNome);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 6, 6, 6);
        painel.add(botaoEntrar, c);

        rotuloErro.setForeground(new Color(180, 30, 30));
        c.gridy = 5;
        c.insets = new Insets(6, 6, 6, 6);
        painel.add(rotuloErro, c);

        botaoEntrar.addActionListener(e -> entrar());
        return painel;
    }

    private void adicionarLinha(JPanel painel, GridBagConstraints c, int linha,
                                String rotulo, JTextField campo) {
        c.gridx = 0;
        c.gridy = linha;
        c.fill = GridBagConstraints.NONE;
        painel.add(new JLabel(rotulo), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        painel.add(campo, c);
    }

    private JPanel criarTelaChat() {
        JPanel painel = new JPanel(new BorderLayout(8, 8));
        painel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topo = new JPanel(new BorderLayout());
        rotuloTitulo.setFont(rotuloTitulo.getFont().deriveFont(Font.BOLD, 14f));
        topo.add(rotuloTitulo, BorderLayout.CENTER);
        topo.add(botaoSair, BorderLayout.EAST);
        painel.add(topo, BorderLayout.NORTH);

        areaMensagens.setEditable(false);
        areaMensagens.setLineWrap(true);
        areaMensagens.setWrapStyleWord(true);
        areaMensagens.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        areaMensagens.setMargin(new Insets(6, 8, 6, 8));
        painel.add(new JScrollPane(areaMensagens), BorderLayout.CENTER);

        JPanel base = new JPanel(new BorderLayout(6, 0));
        base.add(campoMensagem, BorderLayout.CENTER);
        base.add(botaoEnviar, BorderLayout.EAST);
        painel.add(base, BorderLayout.SOUTH);

        botaoEnviar.addActionListener(e -> enviar());
        campoMensagem.addActionListener(e -> enviar());
        botaoSair.addActionListener(e -> sair());
        return painel;
    }

    private void mostrarEntrada() {
        cartoes.show(raiz, TELA_ENTRADA);
        getRootPane().setDefaultButton(botaoEntrar);
        botaoEntrar.setEnabled(true);
        campoNome.requestFocusInWindow();
    }

    private void mostrarChat(String nome, String servidor, int porta) {
        areaMensagens.setText("");
        rotuloTitulo.setText(nome + "  ·  " + servidor + ":" + porta);
        campoMensagem.setText("");
        campoMensagem.setEnabled(true);
        botaoEnviar.setEnabled(true);
        cartoes.show(raiz, TELA_CHAT);
        getRootPane().setDefaultButton(botaoEnviar);
        campoMensagem.requestFocusInWindow();
    }

    // ---------------------------------------------------------------- ações

    private void entrar() {
        rotuloErro.setText(" ");
        String servidor = campoServidor.getText().strip();
        if (servidor.isEmpty()) {
            rotuloErro.setText("Informe o endereço do servidor.");
            return;
        }
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().strip());
            if (porta < 1 || porta > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            rotuloErro.setText("A porta deve ser um número entre 1 e 65535.");
            return;
        }
        String nome;
        try {
            nome = new Usuario(campoNome.getText()).getNome();
        } catch (IllegalArgumentException e) {
            rotuloErro.setText(e.getMessage());
            return;
        }

        botaoEntrar.setEnabled(false);
        rotuloErro.setText("Conectando...");
        ClienteChat novo = new ClienteChat(this);
        final int portaFinal = porta;

        // A conexão pode demorar: fazemos fora da thread da interface para a janela não travar.
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                novo.conectar(servidor, portaFinal, nome);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    cliente = novo;
                    rotuloErro.setText(" ");
                    mostrarChat(nome, servidor, portaFinal);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable causa = e.getCause();
                    String motivo = (causa instanceof IOException && causa.getMessage() != null)
                            ? causa.getMessage() : "Falha inesperada.";
                    rotuloErro.setText("Não foi possível entrar: " + motivo);
                    botaoEntrar.setEnabled(true);
                }
            }
        }.execute();
    }

    private void enviar() {
        String texto = campoMensagem.getText().strip();
        if (texto.isEmpty() || cliente == null) {
            return;
        }
        if (texto.length() > Mensagem.TAMANHO_MAXIMO) {
            anexar("Sua mensagem passa de " + Mensagem.TAMANHO_MAXIMO + " caracteres.");
            return;
        }
        try {
            cliente.enviar(texto);
            campoMensagem.setText("");
        } catch (IOException e) {
            anexar("Não foi possível enviar: " + e.getMessage());
        }
        campoMensagem.requestFocusInWindow();
    }

    private void sair() {
        if (cliente != null) {
            cliente.desconectar();
            cliente = null;
        }
        mostrarEntrada();
    }

    private void anexar(String linha) {
        areaMensagens.append(linha + "\n");
        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());
    }

    // ------------------------------------------- eventos vindos da rede (outra thread)

    @Override
    public void aoReceberLinha(String linha) {
        SwingUtilities.invokeLater(() -> anexar(linha));
    }

    @Override
    public void aoDesconectar(String motivo) {
        SwingUtilities.invokeLater(() -> {
            anexar("— " + motivo + " Clique em Sair para voltar à tela inicial. —");
            campoMensagem.setEnabled(false);
            botaoEnviar.setEnabled(false);
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // usa o visual padrão do Java
        }
        SwingUtilities.invokeLater(() -> new JanelaChat().setVisible(true));
    }
}
