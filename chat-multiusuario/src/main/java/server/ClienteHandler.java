package server;

import model.Mensagem;
import model.Protocolo;
import model.Usuario;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Atende uma única conexão de cliente, em sua própria thread: lê as linhas que o
 * cliente envia e as repassa ao {@link ServicoChat} (que grava no banco e faz o broadcast).
 *
 * <p>O envio ao cliente é assíncrono: {@link #enviar} só coloca o texto numa fila e uma
 * thread de escrita própria grava no socket. Assim, um cliente que parou de ler não
 * bloqueia o broadcast (nem os outros clientes); se a fila encher, ele é desconectado.
 */
public class ClienteHandler implements Runnable, Destinatario {

    private static final int TEMPO_MAXIMO_IDENTIFICACAO_MS = 30_000;
    /** Textos aguardando envio a um cliente; passou disso, o cliente é considerado lento demais. */
    private static final int LIMITE_FILA = 1000;
    /** Quanto o broadcast espera, no máximo, por vaga na fila de um cliente cheio. */
    private static final int TEMPO_MAXIMO_VAGA_MS = 1_000;
    /** Linhas maiores que isso são cortadas na leitura (a validação rejeita a mensagem depois). */
    private static final int LIMITE_LINHA = Mensagem.TAMANHO_MAXIMO * 4;

    private final Socket socket;
    private final ServicoChat servico;
    private final Consumer<String> log;
    private final BufferedReader entrada;
    private final PrintWriter saida;
    private final Object travaEscrita = new Object();
    private final BlockingQueue<String> fila = new LinkedBlockingQueue<>(LIMITE_FILA);
    private volatile boolean fechado = false;

    private volatile String nome = "?";
    private volatile boolean registrado = false;

    public ClienteHandler(Socket socket, ServicoChat servico, Consumer<String> log)
            throws IOException {
        this.socket = socket;
        this.servico = servico;
        this.log = log;
        this.entrada = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.saida = new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
    }

    @Override
    public void run() {
        Thread escritor = new Thread(this::escreverFila, "escritor-" + socket.getRemoteSocketAddress());
        escritor.setDaemon(true);
        escritor.start();
        try {
            if (!identificar()) {
                return;
            }
            servico.anunciar(nome + " entrou no chat.");
            log.accept(nome + " entrou (" + servico.quantidade() + " conectado(s)).");

            String linha;
            while ((linha = lerLinha()) != null) {
                tratarLinha(linha);
            }
        } catch (IOException e) {
            // Desconexão abrupta (cliente fechou a janela, rede caiu...). Não derruba o servidor.
        } finally {
            encerrar();
        }
    }

    /** Lê o nome, valida e registra no gerenciador. Devolve false se o cliente foi recusado. */
    private boolean identificar() throws IOException {
        socket.setSoTimeout(TEMPO_MAXIMO_IDENTIFICACAO_MS);
        String informado = lerLinha();
        socket.setSoTimeout(0);
        if (informado == null) {
            return false;
        }
        Usuario usuario;
        try {
            usuario = new Usuario(informado);
        } catch (IllegalArgumentException e) {
            recusar(e.getMessage());
            return false;
        }
        nome = usuario.getNome();
        if (!servico.entrar(this)) {
            recusar("O nome \"" + nome + "\" já está em uso.");
            return false;
        }
        registrado = true;
        return true;
    }

    /**
     * Como {@code readLine()}, mas guarda no máximo {@code LIMITE_LINHA + 1} caracteres: o
     * excesso é lido e descartado, para um cliente mal-intencionado não esgotar a memória do
     * servidor. Uma linha cortada continua grande demais e é recusada pela validação.
     */
    private String lerLinha() throws IOException {
        StringBuilder linha = new StringBuilder();
        boolean leuAlgo = false;
        int c;
        while ((c = entrada.read()) != -1) {
            leuAlgo = true;
            if (c == '\n') {
                return linha.toString();
            }
            if (c != '\r' && linha.length() <= LIMITE_LINHA) {
                linha.append((char) c);
            }
        }
        return leuAlgo ? linha.toString() : null;
    }

    private void tratarLinha(String linha) {
        if (linha.isBlank()) {
            return;
        }
        try {
            servico.enviarMensagem(nome, linha);
        } catch (IllegalArgumentException e) {
            // Mensagem inválida (ex.: grande demais): só quem enviou fica sabendo.
            enviar(Mensagem.doSistema(e.getMessage()).formatar());
        }
    }

    private void recusar(String motivo) {
        escreverDireto(Protocolo.PREFIXO_ERRO + motivo); // a conexão será fechada logo em seguida
        log.accept("Conexão recusada: " + motivo);
    }

    private void encerrar() {
        if (registrado) {
            servico.sair(this);
            servico.anunciar(nome + " saiu do chat.");
            log.accept(nome + " saiu (" + servico.quantidade() + " conectado(s)).");
            registrado = false;
        }
        fechar();
    }

    @Override
    public String getNome() {
        return nome;
    }

    /**
     * Enfileira o texto. Se a fila está cheia, espera até {@code TEMPO_MAXIMO_VAGA_MS} por uma
     * vaga (dá fôlego a rajadas de mensagens); passado esse tempo o cliente é considerado
     * parado. Devolve false se o cliente já fechou ou não está acompanhando.
     */
    @Override
    public boolean enviar(String texto) {
        if (fechado) {
            return false;
        }
        try {
            return fila.offer(texto, TEMPO_MAXIMO_VAGA_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Thread de escrita: tira textos da fila e grava no socket, na ordem. */
    private void escreverFila() {
        try {
            while (!fechado) {
                String texto = fila.poll(200, TimeUnit.MILLISECONDS);
                if (texto != null && !escreverDireto(texto)) {
                    fechar();
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean escreverDireto(String texto) {
        synchronized (travaEscrita) {
            saida.print(texto);
            saida.print('\n');
            saida.flush();
            return !saida.checkError();
        }
    }

    @Override
    public void fechar() {
        fechado = true;
        try {
            socket.close();
        } catch (IOException e) {
            // já estava fechado
        }
    }
}
