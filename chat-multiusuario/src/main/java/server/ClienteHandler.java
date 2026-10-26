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
import java.util.function.Consumer;

/**
 * Atende uma única conexão de cliente, em sua própria thread: lê as linhas que o
 * cliente envia e as repassa ao {@link ServicoChat} (que grava no banco e faz o broadcast).
 */
public class ClienteHandler implements Runnable, Destinatario {

    private static final int TEMPO_MAXIMO_IDENTIFICACAO_MS = 30_000;

    private final Socket socket;
    private final ServicoChat servico;
    private final Consumer<String> log;
    private final BufferedReader entrada;
    private final PrintWriter saida;
    private final Object travaEscrita = new Object();

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
        try {
            if (!identificar()) {
                return;
            }
            servico.anunciar(nome + " entrou no chat.");
            log.accept(nome + " entrou (" + servico.quantidade() + " conectado(s)).");

            String linha;
            while ((linha = entrada.readLine()) != null) {
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
        String informado = entrada.readLine();
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
        enviar(Protocolo.PREFIXO_ERRO + motivo);
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

    @Override
    public boolean enviar(String texto) {
        synchronized (travaEscrita) {
            saida.print(texto);
            saida.print('\n');
            saida.flush();
            return !saida.checkError();
        }
    }

    @Override
    public void fechar() {
        try {
            socket.close();
        } catch (IOException e) {
            // já estava fechado
        }
    }
}
