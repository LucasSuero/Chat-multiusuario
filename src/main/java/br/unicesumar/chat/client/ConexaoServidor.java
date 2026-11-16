package br.unicesumar.chat.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uma conexão TCP com o servidor: uma thread lê linhas, outra (fila de um só escritor) envia.
 * Quem chama nunca bloqueia no envio, então a interface não congela se a rede travar.
 * Só entrega linhas brutas; a interpretação do protocolo é do ClienteChat.
 */
final class ConexaoServidor {

    interface Ouvinte {
        void aoReceber(ConexaoServidor origem, String linha);

        /** Chamado uma única vez quando a conexão cai sem que o cliente tenha pedido o fechamento. */
        void aoPerder(ConexaoServidor origem, String motivo);
    }

    private static final Logger LOG = Logger.getLogger(ConexaoServidor.class.getName());

    private final Ouvinte ouvinte;
    private final ExecutorService escritor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "escritor-servidor");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean notificado = new AtomicBoolean();
    private volatile boolean voluntario;
    private volatile Socket socket;
    private BufferedReader entrada;
    private BufferedWriter saida;

    ConexaoServidor(Ouvinte ouvinte) {
        this.ouvinte = ouvinte;
    }

    /** Abre o socket (bloqueia até o timeout). Ainda não começa a ler. */
    void abrir(String host, int porta, int timeoutMs) throws IOException {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, porta), timeoutMs);
            s.setTcpNoDelay(true);
            entrada = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            saida = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            s.close();
            throw e;
        }
        socket = s;
    }

    /** Começa a ler do servidor. Chamar depois de registrar esta conexão como a atual. */
    void iniciar() {
        Thread.ofPlatform().daemon().name("leitor-servidor").start(this::ler);
    }

    private void ler() {
        try {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                try {
                    ouvinte.aoReceber(this, linha);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Erro ao tratar linha do servidor", e);
                }
            }
            perdeu("O servidor encerrou a conexão");
        } catch (IOException e) {
            perdeu("Conexão perdida: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Enfileira uma linha para envio. */
    void enviar(String linha) {
        try {
            escritor.execute(() -> {
                try {
                    saida.write(linha);
                    saida.write('\n');
                    saida.flush();
                } catch (IOException e) {
                    perdeu("Falha ao enviar: " + (e.getMessage() == null ? "erro de rede" : e.getMessage()));
                }
            });
        } catch (RejectedExecutionException e) {
            LOG.fine("Envio ignorado: conexão já encerrada");
        }
    }

    boolean aberta() {
        Socket s = socket;
        return s != null && !s.isClosed() && !voluntario && !notificado.get();
    }

    /** Fecha a pedido do cliente (não gera aoPerder). O que já estava na fila é enviado antes. */
    void fechar() {
        voluntario = true;
        try {
            escritor.execute(this::fecharSocket);
        } catch (RejectedExecutionException e) {
            fecharSocket();
        }
        escritor.shutdown();
        Thread.ofPlatform().daemon().name("fechamento-servidor").start(() -> {
            try {
                Thread.sleep(2000); // garantia caso o escritor esteja preso
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            fecharSocket();
        });
    }

    private void perdeu(String motivo) {
        if (!voluntario && notificado.compareAndSet(false, true)) {
            fecharSocket();
            escritor.shutdownNow();
            ouvinte.aoPerder(this, motivo);
        }
    }

    private void fecharSocket() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                LOG.log(Level.FINE, "Erro ao fechar socket", e);
            }
        }
    }
}
