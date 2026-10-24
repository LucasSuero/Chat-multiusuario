package client;

import model.Protocolo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lado cliente da conexão: conecta ao servidor, envia mensagens e avisa um
 * {@link Ouvinte} sempre que chega uma linha nova. Não conhece a interface gráfica.
 */
public class ClienteChat {

    private static final int TEMPO_MAXIMO_CONEXAO_MS = 5_000;
    private static final int TEMPO_MAXIMO_RESPOSTA_MS = 10_000;

    /** Quem quer ser avisado dos eventos da conexão (chamado a partir de outra thread). */
    public interface Ouvinte {
        void aoReceberLinha(String linha);

        void aoDesconectar(String motivo);
    }

    private final Ouvinte ouvinte;
    private final AtomicBoolean conectado = new AtomicBoolean(false);
    private final Object travaEscrita = new Object();

    private Socket socket;
    private BufferedReader entrada;
    private PrintWriter saida;

    public ClienteChat(Ouvinte ouvinte) {
        this.ouvinte = ouvinte;
    }

    /**
     * Conecta e se identifica. Bloqueia até o servidor responder, então não deve
     * ser chamado na thread da interface gráfica.
     *
     * @throws IOException se não conseguir conectar ou se o servidor recusar o nome
     */
    public void conectar(String host, int porta, String nome) throws IOException {
        Socket novo = new Socket();
        try {
            novo.connect(new InetSocketAddress(host, porta), TEMPO_MAXIMO_CONEXAO_MS);
            novo.setSoTimeout(TEMPO_MAXIMO_RESPOSTA_MS);
            BufferedReader leitor = new BufferedReader(
                    new InputStreamReader(novo.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter escritor = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(novo.getOutputStream(), StandardCharsets.UTF_8)));

            escritor.print(nome);
            escritor.print('\n');
            escritor.flush();

            String resposta = leitor.readLine();
            if (resposta == null) {
                throw new IOException("O servidor encerrou a conexão.");
            }
            if (resposta.startsWith(Protocolo.PREFIXO_ERRO)) {
                throw new IOException(resposta.substring(Protocolo.PREFIXO_ERRO.length()));
            }
            if (!resposta.equals(Protocolo.OK)) {
                throw new IOException("Resposta inesperada do servidor.");
            }
            novo.setSoTimeout(0);

            this.socket = novo;
            this.entrada = leitor;
            this.saida = escritor;
        } catch (IOException e) {
            fecharQuieto(novo);
            throw e;
        }

        conectado.set(true);
        Thread leitora = new Thread(this::receber, "leitor-do-servidor");
        leitora.setDaemon(true);
        leitora.start();
    }

    private void receber() {
        String motivo = "A conexão com o servidor foi encerrada.";
        try {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                ouvinte.aoReceberLinha(linha);
            }
        } catch (IOException e) {
            // conexão caiu ou foi fechada por nós
        }
        // Só avisa se não fomos nós que pedimos para sair.
        if (conectado.getAndSet(false)) {
            fecharQuieto(socket);
            ouvinte.aoDesconectar(motivo);
        }
    }

    public void enviar(String texto) throws IOException {
        if (!conectado.get()) {
            throw new IOException("Você não está conectado.");
        }
        synchronized (travaEscrita) {
            saida.print(texto);
            saida.print('\n');
            saida.flush();
            if (saida.checkError()) {
                throw new IOException("Não foi possível enviar a mensagem.");
            }
        }
    }

    public void desconectar() {
        conectado.set(false);
        fecharQuieto(socket);
    }

    public boolean estaConectado() {
        return conectado.get();
    }

    private static void fecharQuieto(Socket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException e) {
            // nada a fazer
        }
    }
}
