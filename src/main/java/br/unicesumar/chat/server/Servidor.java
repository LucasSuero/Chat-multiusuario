package br.unicesumar.chat.server;

import br.unicesumar.chat.protocol.Eventos;
import br.unicesumar.chat.service.Erro;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Socket de escuta + laço que aceita conexões. Cada conexão vira um ClienteHandler rodando em sua
 * própria thread, registrado na lista sincronizada de sessões ativas.
 */
public class Servidor {

    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());
    public static final int MAX_CONEXOES_PADRAO = 200;

    private final int porta;
    private final ProcessadorComandos processador;
    private final RegistroSessoes registro;
    private final int maxConexoes;
    private final AtomicInteger contador = new AtomicInteger();
    private volatile boolean parado;
    private ServerSocket serverSocket;
    private Thread aceitador;

    public Servidor(int porta, ProcessadorComandos processador, RegistroSessoes registro) {
        this(porta, processador, registro, MAX_CONEXOES_PADRAO);
    }

    public Servidor(int porta, ProcessadorComandos processador, RegistroSessoes registro, int maxConexoes) {
        this.porta = porta;
        this.processador = processador;
        this.registro = registro;
        this.maxConexoes = maxConexoes;
    }

    public void iniciar() throws IOException {
        serverSocket = new ServerSocket(porta);
        aceitador = Thread.ofPlatform().name("aceitador").start(this::aceitar);
        LOG.info("Servidor ouvindo na porta " + porta());
    }

    /** Porta realmente usada (útil quando se passa 0 para escolher uma porta livre). */
    public int porta() {
        return serverSocket.getLocalPort();
    }

    private void aceitar() {
        while (!parado) {
            try {
                Socket socket = serverSocket.accept();
                if (registro.total() >= maxConexoes) {
                    rejeitar(socket);
                    continue;
                }
                ClienteHandler handler = new ClienteHandler(socket, processador, registro);
                registro.adicionar(handler);
                try {
                    Thread.ofPlatform().name("cliente-" + contador.incrementAndGet()).start(handler);
                } catch (RuntimeException e) {
                    registro.remover(handler);
                    throw e;
                }
            } catch (IOException e) {
                if (!parado) {
                    LOG.log(Level.WARNING, "Erro ao aceitar conexão", e);
                }
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Falha ao iniciar atendimento de cliente", e);
            }
        }
    }

    private void rejeitar(Socket socket) {
        try (socket) {
            socket.getOutputStream().write(
                    (Eventos.erro(Erro.SERVIDOR_CHEIO, "Servidor lotado, tente mais tarde") + "\n")
                            .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Falha ao rejeitar conexão", e);
        }
    }

    public void parar() {
        parado = true;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Erro ao fechar socket de escuta", e);
        }
        for (Sessao s : registro.snapshot()) {
            s.fechar();
        }
        if (aceitador != null) {
            try {
                aceitador.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
