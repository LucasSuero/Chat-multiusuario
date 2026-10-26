package server;

import persistence.ConfiguracaoBanco;
import persistence.MensagemRepository;
import persistence.RepositorioException;
import persistence.RepositorioMensagens;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Servidor do chat: escuta numa porta e, para cada conexão aceita, cria um
 * {@link ClienteHandler} e o entrega a uma nova thread.
 */
public class Servidor {

    private final GerenciadorClientes gerenciador = new GerenciadorClientes();
    private final RepositorioMensagens repositorio;
    private final ServicoChat servico;
    private final Consumer<String> log;
    private volatile ServerSocket socketEscuta;

    /** Servidor com histórico gravado no MySQL (ver {@link ConfiguracaoBanco}). */
    public Servidor(Consumer<String> log) {
        this(new MensagemRepository(ConfiguracaoBanco.padrao()), log);
    }

    public Servidor(RepositorioMensagens repositorio, Consumer<String> log) {
        this.repositorio = repositorio;
        this.log = log;
        this.servico = new ServicoChat(gerenciador, repositorio, log);
    }

    /** Abre a porta e começa a aceitar clientes em segundo plano. */
    public void iniciar(int porta) throws IOException {
        if (estaRodando()) {
            throw new IllegalStateException("O servidor já está rodando.");
        }
        ServerSocket novo = new ServerSocket(porta);
        socketEscuta = novo;
        Thread aceitador = new Thread(() -> aceitarConexoes(novo), "aceitador-de-conexoes");
        aceitador.setDaemon(true);
        aceitador.start();
        log.accept("Servidor iniciado na porta " + novo.getLocalPort() + ".");
        verificarBanco();
    }

    /** Testa o banco em segundo plano (a conexão pode demorar e não deve travar a janela). */
    private void verificarBanco() {
        Thread t = new Thread(() -> {
            try {
                repositorio.buscarUltimas(1);
                log.accept("Banco de dados conectado: o histórico está ativo.");
            } catch (RepositorioException e) {
                log.accept("AVISO: banco de dados indisponível. O chat funciona, mas as mensagens "
                        + "não são gravadas e o histórico fica vazio.");
            }
        }, "verificacao-do-banco");
        t.setDaemon(true);
        t.start();
    }

    private void aceitarConexoes(ServerSocket escuta) {
        while (!escuta.isClosed()) {
            Socket socket;
            try {
                socket = escuta.accept();
            } catch (IOException e) {
                if (!escuta.isClosed()) {
                    log.accept("Erro ao aceitar conexão: " + e.getMessage());
                }
                continue;
            }
            try {
                ClienteHandler handler = new ClienteHandler(socket, servico, log);
                Thread thread = new Thread(handler, "cliente-" + socket.getRemoteSocketAddress());
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                log.accept("Não foi possível atender " + socket.getRemoteSocketAddress()
                        + ": " + e.getMessage());
                try {
                    socket.close();
                } catch (IOException ignorada) {
                    // nada a fazer
                }
            }
        }
    }

    /** Para de aceitar conexões e desconecta quem estava conectado. */
    public void parar() {
        ServerSocket escuta = socketEscuta;
        socketEscuta = null;
        if (escuta == null) {
            return;
        }
        try {
            escuta.close();
        } catch (IOException e) {
            // já estava fechado
        }
        gerenciador.desconectarTodos();
        log.accept("Servidor parado.");
    }

    public boolean estaRodando() {
        ServerSocket escuta = socketEscuta;
        return escuta != null && !escuta.isClosed();
    }

    /** Porta em que o servidor está escutando (útil quando iniciado na porta 0). */
    public int getPorta() {
        ServerSocket escuta = socketEscuta;
        return escuta == null ? -1 : escuta.getLocalPort();
    }

    public int getQuantidadeClientes() {
        return gerenciador.quantidade();
    }
}
