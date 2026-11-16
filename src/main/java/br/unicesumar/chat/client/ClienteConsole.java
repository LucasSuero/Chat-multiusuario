package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.service.FormatadorMensagem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Cliente de terminal: conecta ao servidor, envia o que o usuário digita para a sala Geral (broadcast) e
 * exibe as mensagens recebidas. Usa o mesmo {@link ClienteChat} do cliente gráfico, sem JavaFX.
 *
 * Uso: ClienteConsole host porta usuario senha [novo]   (com "novo" cria a conta antes de entrar)
 */
public final class ClienteConsole implements ClienteChat.Ouvinte {

    private static final int ESPERA_SEGUNDOS = 10;

    private final ClienteChat chat;
    private final PrintStream saida;
    private final ZoneId zona;
    private final CompletableFuture<Void> autenticacao = new CompletableFuture<>();
    private final CountDownLatch historicoRecebido = new CountDownLatch(1);
    private volatile long idGeral;
    private volatile boolean autenticado;

    public ClienteConsole(ClienteChat chat, PrintStream saida, ZoneId zona) {
        this.chat = chat;
        this.saida = saida;
        this.zona = zona;
        chat.definirOuvinte(this);
    }

    /** Conecta, autentica e conversa na sala Geral lendo linhas de {@code entrada} até o fim ou "/sair". */
    public int executar(String host, int porta, String usuario, String senha, boolean cadastrar,
                        BufferedReader entrada) throws IOException, InterruptedException {
        try {
            chat.conectar(host, porta);
        } catch (IOException e) {
            saida.println("Não foi possível conectar a " + host + ":" + porta + " (" + e.getMessage() + ")");
            return 1;
        }
        if (cadastrar) {
            chat.registrar(usuario, senha, Avatares.PADRAO_USUARIO);
        } else {
            chat.login(usuario, senha);
        }
        try {
            autenticacao.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            saida.println("Falha ao entrar: " + (e.getCause() != null ? e.getCause().getMessage() : "tempo esgotado"));
            chat.sair();
            return 1;
        }
        historicoRecebido.await(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        saida.println("--- conectado como " + usuario + ". Escreva para a sala Geral; /sair encerra. ---");
        String linha;
        while ((linha = entrada.readLine()) != null) {
            if (linha.strip().equals("/sair")) {
                break;
            }
            if (!linha.isBlank() && idGeral != 0) {
                chat.enviarMensagem(idGeral, linha);
            }
        }
        chat.sair();
        return 0;
    }

    @Override
    public void onAutenticado(br.unicesumar.chat.model.Usuario usuario) {
        autenticado = true;
        autenticacao.complete(null);
    }

    @Override
    public void onHistoricoCarregado(long conversaId) {
        if (idGeral == 0) {
            idGeral = conversaId; // o primeiro histórico, enviado no login, é o da sala Geral
            saida.println("--- fim do histórico da sala Geral ---");
            historicoRecebido.countDown();
        }
    }

    @Override
    public void onMensagem(Mensagem m) {
        String prefixo = idGeral != 0 && m.conversaId() != idGeral ? "[conversa " + m.conversaId() + "] " : "";
        saida.println(prefixo + FormatadorMensagem.formatar(m, zona));
    }

    @Override
    public void onErro(String codigo, String mensagem) {
        if (!autenticado) {
            autenticacao.completeExceptionally(new IllegalStateException(mensagem));
        } else {
            saida.println("(erro: " + mensagem + ")");
        }
    }

    @Override
    public void onDesconectado(String motivo) {
        saida.println("--- conexão perdida: " + motivo + " ---");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Uso: ClienteConsole host porta usuario senha [novo]");
            System.exit(2);
        }
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        ClienteConsole cliente = new ClienteConsole(new ClienteChat(), System.out, ZoneId.systemDefault());
        boolean novo = args.length > 4 && args[4].equalsIgnoreCase("novo");
        System.exit(cliente.executar(args[0], Integer.parseInt(args[1]), args[2], args[3], novo, teclado));
    }
}
