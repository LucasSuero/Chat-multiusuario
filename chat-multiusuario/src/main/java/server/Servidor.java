package server;

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
    private final Consumer<String> log;
    private volatile ServerSocket socketEscuta;

    public Servidor(Consumer<String> log) {
        this.log = log;
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
                ClienteHandler handler = new ClienteHandler(socket, gerenciador, log);
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
