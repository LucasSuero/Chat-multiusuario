package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Eventos;
import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.service.Erro;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trata uma única conexão de cliente. A thread de ClienteHandler só LÊ do socket e processa comandos;
 * a escrita passa por uma fila limitada e uma segunda thread. Assim, enviar para um cliente lento ou
 * travado nunca bloqueia quem está enviando (outros clientes e o broadcast).
 */
public class ClienteHandler implements Runnable, Sessao {

    private static final Logger LOG = Logger.getLogger(ClienteHandler.class.getName());
    private static final int CAPACIDADE_SAIDA = 1000;
    private static final int TIMEOUT_SEM_LOGIN_MS = 60_000;
    private static final String FIM = new String("<fim>"); // sentinela comparada por identidade

    private final Socket socket;
    private final ProcessadorComandos processador;
    private final RegistroSessoes registro;
    private final String origem;
    private final BlockingQueue<String> saida = new LinkedBlockingQueue<>(CAPACIDADE_SAIDA);
    private final AtomicBoolean aberta = new AtomicBoolean(true);
    private volatile Usuario usuario;

    public ClienteHandler(Socket socket, ProcessadorComandos processador, RegistroSessoes registro) {
        this.socket = socket;
        this.processador = processador;
        this.registro = registro;
        this.origem = String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void run() {
        LOG.info("Cliente conectado: " + origem);
        Thread.ofPlatform().daemon().name("escritor-" + origem).start(this::escrever);
        try {
            socket.setSoTimeout(TIMEOUT_SEM_LOGIN_MS);
            BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String linha;
            while (aberta.get() && (linha = lerLinha(entrada)) != null) {
                if (!linha.isBlank() && !processador.processar(this, linha)) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            enviar(Eventos.erro(Erro.TEMPO_ESGOTADO, "Conexão encerrada por inatividade"));
        } catch (IOException e) {
            LOG.log(Level.FINE, "Conexão encerrada: " + origem, e);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Erro inesperado no cliente " + origem, e);
        } finally {
            fechar();
            registro.remover(this);
            LOG.info("Cliente desconectado: " + origem
                    + (usuario != null ? " (" + usuario.username() + ")" : ""));
        }
    }

    /** Lê uma linha com limite de tamanho, para um cliente mal-comportado não esgotar a memória. */
    private static String lerLinha(BufferedReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                return sb.toString();
            }
            if (c != '\r') {
                sb.append((char) c);
                if (sb.length() > Protocolo.MAX_LINHA) {
                    throw new IOException("Linha maior que o limite do protocolo");
                }
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private void escrever() {
        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            while (true) {
                String linha = saida.take();
                if (linha == FIM) {
                    break;
                }
                out.write(linha);
                out.write('\n');
                if (saida.isEmpty()) {
                    out.flush();
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Falha ao escrever para " + origem, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            aberta.set(false);
            fecharSocket();
        }
    }

    @Override
    public Optional<Usuario> usuario() {
        return Optional.ofNullable(usuario);
    }

    @Override
    public void definirUsuario(Usuario usuario) {
        this.usuario = usuario;
        try {
            socket.setSoTimeout(0); // depois do login, o cliente pode ficar ocioso
        } catch (SocketException e) {
            LOG.log(Level.FINE, "Não foi possível ajustar o timeout", e);
        }
    }

    @Override
    public void enviar(String linha) {
        if (!aberta.get()) {
            return;
        }
        if (!saida.offer(linha)) {
            LOG.warning("Cliente lento, fila cheia. Desconectando " + origem);
            abortar();
        }
    }

    /** Encerramento ordenado: o que já está na fila é enviado antes de fechar o socket. */
    @Override
    public void fechar() {
        if (aberta.compareAndSet(true, false) && !saida.offer(FIM)) {
            abortar();
        }
    }

    private void abortar() {
        aberta.set(false);
        saida.clear();
        saida.offer(FIM);
        fecharSocket();
    }

    private void fecharSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Erro ao fechar socket", e);
        }
    }
}
