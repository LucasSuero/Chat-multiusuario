package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.Database;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.server.ProcessadorComandos;
import br.unicesumar.chat.server.RegistroSessoes;
import br.unicesumar.chat.server.Roteador;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.service.ServicoChat;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integração de ponta a ponta com o BANCO REAL (SQLite em arquivo): Servidor + repositórios JDBC + ClienteChat,
 * por sockets reais em localhost. Nenhum repositório em memória é usado aqui.
 */
class ServidorSqliteIntegracaoTest {

    private static final String SQL_GERAL = "SELECT COUNT(*) FROM conversa WHERE tipo = 'GRUPO' "
            + "AND criador_id IS NULL AND nome = 'Geral'";

    private record Cliente(ClienteChat chat, ClienteChatIntegracaoTest.Gravador eventos, long idGeral) {
    }

    private Path arquivo;
    private Database db;
    private Servidor servidor;
    private final List<ClienteChat> clientes = new ArrayList<>();

    private Servidor subirServidor() throws IOException {
        db = Database.emArquivo(arquivo);
        db.inicializar();
        ServicoChat servico = new ServicoChat(new UsuarioRepository(db), new ConversaRepository(db),
                new MensagemRepository(db), Clock.systemUTC());
        RegistroSessoes registro = new RegistroSessoes();
        Servidor s = new Servidor(0, new ProcessadorComandos(servico, new Roteador(servico, registro)), registro);
        s.iniciar();
        return s;
    }

    @BeforeEach
    void preparar() throws IOException {
        arquivo = Files.createTempDirectory("chat-sqlite").resolve("chat.db");
        servidor = subirServidor();
    }

    @AfterEach
    void encerrar() {
        clientes.forEach(ClienteChat::sair);
        servidor.parar();
    }

    private static <T> T esperar(java.util.concurrent.BlockingQueue<T> fila) throws InterruptedException {
        return ClienteChatIntegracaoTest.Gravador.esperar(fila);
    }

    private static boolean nadaChega(java.util.concurrent.BlockingQueue<?> fila, long ms) throws InterruptedException {
        return ClienteChatIntegracaoTest.Gravador.nadaChega(fila, ms);
    }

    private Cliente conectar(String nome, boolean cadastro, String avatar) throws Exception {
        ClienteChat c = new ClienteChat();
        ClienteChatIntegracaoTest.Gravador g = new ClienteChatIntegracaoTest.Gravador();
        c.definirOuvinte(g);
        c.conectar("localhost", servidor.porta());
        clientes.add(c);
        if (cadastro) {
            c.registrar(nome, "segredo1", avatar);
        } else {
            c.login(nome, "segredo1");
        }
        esperar(g.autenticados);
        long geral = esperar(g.historicos); // histórico da Geral chega sozinho depois da autenticação
        return new Cliente(c, g, geral);
    }

    private Cliente cadastrar(String nome, String avatar) throws Exception {
        return conectar(nome, true, avatar);
    }

    private int contar(String sql) throws SQLException {
        try (Connection c = db.abrir(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void aguardarTotal(Cliente c, long geral, int esperado) throws InterruptedException {
        for (int i = 0; i < 400 && c.chat().mensagens(geral).size() < esperado; i++) {
            Thread.sleep(50);
        }
        assertEquals(esperado, c.chat().mensagens(geral).size());
    }

    // ---- cenários 1, 2, 3: três clientes, broadcast na Geral ----

    @Test
    void tresClientesSimultaneosEBroadcastNaGeralChegaAosOutrosDois() throws Exception {
        Cliente ana = cadastrar("ana", "avatar02");
        Cliente bruno = cadastrar("bruno", "avatar03");
        Cliente carla = cadastrar("carla", "avatar04");
        assertEquals(ana.idGeral(), bruno.idGeral());
        assertEquals(ana.idGeral(), carla.idGeral());

        ana.chat().enviarMensagem(ana.idGeral(), "olá, sala geral");
        Mensagem noBruno = esperar(bruno.eventos().mensagens);
        Mensagem naCarla = esperar(carla.eventos().mensagens);
        assertEquals("olá, sala geral", noBruno.conteudo());
        assertEquals("ana", noBruno.remetente().username());
        assertEquals("avatar02", noBruno.remetente().avatar());
        assertEquals(noBruno.id(), naCarla.id());
        assertEquals(1, repoMensagens().buscarUltimas(50).size(), "gravada uma única vez no banco");

        // o remetente recebe só uma cópia de confirmação (a própria mensagem já gravada), e ninguém recebe duas
        assertEquals(noBruno.id(), esperar(ana.eventos().mensagens).id());
        assertTrue(nadaChega(ana.eventos().mensagens, 300));
        assertTrue(nadaChega(bruno.eventos().mensagens, 100));
        assertTrue(nadaChega(carla.eventos().mensagens, 100));
    }

    // ---- cenários 4 e 5: histórico ao entrar + mensagens novas depois ----

    @Test
    void quemEntraRecebeOHistoricoDaGeralEDepoisContinuaRecebendoMensagensNovas() throws Exception {
        Cliente ana = cadastrar("ana", "avatar01");
        for (String texto : List.of("um", "dois", "três")) {
            ana.chat().enviarMensagem(ana.idGeral(), texto);
            esperar(ana.eventos().mensagens);
        }
        Cliente bruno = cadastrar("bruno", "avatar02"); // nenhum ENTRAR é enviado
        assertEquals(List.of("um", "dois", "três"),
                bruno.chat().mensagens(bruno.idGeral()).stream().map(Mensagem::conteudo).toList());
        assertEquals("um", esperar(bruno.eventos().mensagens).conteudo());
        assertEquals("dois", esperar(bruno.eventos().mensagens).conteudo());
        assertEquals("três", esperar(bruno.eventos().mensagens).conteudo());

        ana.chat().enviarMensagem(ana.idGeral(), "quatro");
        assertEquals("quatro", esperar(bruno.eventos().mensagens).conteudo(), "mensagens novas seguem chegando");
        bruno.chat().enviarMensagem(bruno.idGeral(), "cinco");
        assertEquals("cinco", esperarConteudo(ana, "cinco"));
    }

    // ---- cenário 6: privadas e grupos não interferem no broadcast ----

    @Test
    void conversasParticularesEGruposNaoInterferemNoBroadcastGeral() throws Exception {
        Cliente ana = cadastrar("ana", "avatar01");
        Cliente bruno = cadastrar("bruno", "avatar02");
        Cliente carla = cadastrar("carla", "avatar03");
        long geral = ana.idGeral();

        ana.chat().abrirPrivada(bruno.chat().usuario().orElseThrow().id());
        long privada = esperar(ana.eventos().conversas).conversa().id();
        ana.chat().enviarMensagem(privada, "segredo entre ana e bruno");
        assertEquals("segredo entre ana e bruno", esperar(bruno.eventos().mensagens).conteudo());
        assertTrue(nadaChega(carla.eventos().mensagens, 300), "a carla não recebe a conversa privada");

        ana.chat().criarGrupo("Turma", "grupo02", List.of(bruno.chat().usuario().orElseThrow().id()));
        long grupo = esperar(ana.eventos().conversas).conversa().id();
        bruno.chat().enviarMensagem(grupo, "só para o grupo");
        assertEquals("só para o grupo", esperarConteudo(ana, "só para o grupo"));
        assertTrue(nadaChega(carla.eventos().mensagens, 300), "a carla não está no grupo");

        carla.chat().enviarMensagem(geral, "para todos");
        assertEquals("para todos", esperarConteudo(ana, "para todos"));
        assertEquals("para todos", esperarConteudo(bruno, "para todos"));
        assertEquals("para todos", esperarConteudo(carla, "para todos"));

        List<String> historicoGeral = repoMensagens().buscarUltimas(50).stream().map(Mensagem::conteudo).toList();
        assertEquals(List.of("para todos"), historicoGeral, "privadas e grupos não entram no histórico da Geral");
        assertEquals(3, contar("SELECT COUNT(*) FROM mensagem"), "as três mensagens estão no banco, cada uma no seu lugar");
    }

    /** Lê mensagens do cliente até achar o conteúdo esperado (outras, de outras conversas, são ignoradas). */
    private static String esperarConteudo(Cliente c, String conteudo) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            Mensagem m = esperar(c.eventos().mensagens);
            if (m.conteudo().equals(conteudo)) {
                return m.conteudo();
            }
        }
        return "não chegou: " + conteudo;
    }

    // ---- cenário 7: desconexão não derruba os outros nem o servidor ----

    @Test
    void desconexaoAbruptaDeUmClienteNaoDerrubaOsOutrosNemOServidor() throws Exception {
        Cliente ana = cadastrar("ana", "avatar01");
        Cliente carla = cadastrar("carla", "avatar03");
        Socket bruto = conectarBruto("bruno");
        bruto.setSoLinger(true, 0); // RST: simula queda de rede ou processo morto
        bruto.close();

        ana.chat().enviarMensagem(ana.idGeral(), "alguém aí?");
        assertEquals("alguém aí?", esperar(carla.eventos().mensagens).conteudo());
        assertEquals("alguém aí?", esperar(ana.eventos().mensagens).conteudo());

        Cliente diego = cadastrar("diego", "avatar04"); // o servidor continua aceitando gente nova
        carla.chat().enviarMensagem(carla.idGeral(), "bem-vindo");
        assertEquals("bem-vindo", esperarConteudo(diego, "bem-vindo"));
    }

    private Socket conectarBruto(String nome) throws IOException {
        Socket s = new Socket("localhost", servidor.porta());
        s.setSoTimeout(5000);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        w.write(Protocolo.codificar("REGISTRAR", nome, "segredo1", "avatar01"));
        w.write('\n');
        w.flush();
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        String linha;
        while ((linha = r.readLine()) != null && !linha.startsWith("HISTORICO_FIM")) {
            // consome AUTENTICADO e o histórico
        }
        return s;
    }

    // ---- cenário 8: reiniciar o servidor não apaga o histórico; Geral não duplica ----

    @Test
    void reiniciarOServidorNaoApagaOHistoricoNemDuplicaASalaGeral() throws Exception {
        Cliente ana = cadastrar("ana", "avatar02");
        Cliente bruno = cadastrar("bruno", "avatar03");
        long geral = ana.idGeral();
        assertEquals(1, contar(SQL_GERAL));

        ana.chat().enviarMensagem(geral, "na geral antes de reiniciar");
        esperar(ana.eventos().mensagens);
        ana.chat().abrirPrivada(bruno.chat().usuario().orElseThrow().id());
        long privada = esperar(ana.eventos().conversas).conversa().id();
        ana.chat().enviarMensagem(privada, "privada antes de reiniciar");
        esperarConteudo(ana, "privada antes de reiniciar");
        ana.chat().criarGrupo("Turma", "grupo02", List.of(bruno.chat().usuario().orElseThrow().id()));
        long grupo = esperar(ana.eventos().conversas).conversa().id();
        ana.chat().enviarMensagem(grupo, "grupo antes de reiniciar");
        esperarConteudo(ana, "grupo antes de reiniciar");
        ana.chat().alterarAvatar("avatar09");
        assertEquals("avatar09", esperar(ana.eventos().perfis).avatar());

        clientes.forEach(ClienteChat::sair);
        clientes.clear();
        servidor.parar();
        servidor = subirServidor(); // mesmo arquivo de banco; inicializar() roda o script de novo

        assertEquals(1, contar(SQL_GERAL), "a sala Geral não foi duplicada pelo reinício");
        assertEquals(geral, new ConversaRepository(db).obterOuCriarGeral().id());

        Cliente carla = cadastrar("carla", "avatar04"); // usuário novo depois do reinício
        assertEquals(geral, carla.idGeral());
        assertEquals(1, contar(SQL_GERAL), "cadastrar novos usuários também não duplica a Geral");
        assertEquals(List.of("na geral antes de reiniciar"),
                carla.chat().mensagens(geral).stream().map(Mensagem::conteudo).toList());
        carla.chat().carregarConversas();
        List<ConversaVisao> deCarla = esperar(carla.eventos().listagens);
        assertEquals(1, deCarla.size(), "a carla só participa da Geral");
        carla.chat().entrarNaConversa(privada);
        assertEquals("CONVERSA_INACESSIVEL", esperar(carla.eventos().erros));

        Cliente anaDeNovo = conectar("ana", false, null);
        assertEquals("avatar09", anaDeNovo.chat().usuario().orElseThrow().avatar(), "o avatar ficou gravado");
        assertEquals("na geral antes de reiniciar", anaDeNovo.chat().mensagens(geral).get(0).conteudo());
        anaDeNovo.chat().carregarConversas();
        assertEquals(3, esperar(anaDeNovo.eventos().listagens).size(), "Geral + privada + grupo");
        anaDeNovo.chat().entrarNaConversa(privada);
        esperarHistorico(anaDeNovo, privada);
        assertEquals("privada antes de reiniciar", anaDeNovo.chat().mensagens(privada).get(0).conteudo());
        anaDeNovo.chat().entrarNaConversa(grupo);
        esperarHistorico(anaDeNovo, grupo);
        assertEquals("grupo antes de reiniciar", anaDeNovo.chat().mensagens(grupo).get(0).conteudo());

        // e o chat segue funcionando normalmente depois do reinício
        carla.chat().enviarMensagem(geral, "depois de reiniciar");
        assertEquals("depois de reiniciar", esperarConteudo(anaDeNovo, "depois de reiniciar"));
    }

    private static void esperarHistorico(Cliente c, long conversaId) throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            if (esperar(c.eventos().historicos) == conversaId) {
                return;
            }
        }
        fail("histórico da conversa " + conversaId + " não chegou");
    }

    // ---- concorrência com banco real ----

    @Test
    void tresClientesEnviandoAoMesmoTempoNaoPerdemNemDuplicamNemEmbaralham() throws Exception {
        Cliente ana = cadastrar("ana", "avatar01");
        Cliente bruno = cadastrar("bruno", "avatar02");
        Cliente carla = cadastrar("carla", "avatar03");
        long geral = ana.idGeral();
        int porCliente = 15;
        List<Thread> threads = new ArrayList<>();
        for (Cliente c : List.of(ana, bruno, carla)) {
            String nome = c.chat().usuario().orElseThrow().username();
            Thread t = new Thread(() -> {
                for (int i = 0; i < porCliente; i++) {
                    c.chat().enviarMensagem(geral, nome + "-" + i);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        int total = 3 * porCliente;
        for (Cliente c : List.of(ana, bruno, carla)) {
            aguardarTotal(c, geral, total);
        }
        List<Long> idsAna = ana.chat().mensagens(geral).stream().map(Mensagem::id).toList();
        assertEquals(idsAna, bruno.chat().mensagens(geral).stream().map(Mensagem::id).toList(), "mesma ordem para todos");
        assertEquals(idsAna, carla.chat().mensagens(geral).stream().map(Mensagem::id).toList(), "mesma ordem para todos");
        assertEquals(total, idsAna.stream().distinct().count());
        assertEquals(total, contar("SELECT COUNT(*) FROM mensagem"), "nada perdido nem gravado em duplicidade");
        assertEquals(idsAna, repoMensagens().buscarUltimas(100).stream().map(Mensagem::id).toList(),
                "a ordem entregue é a ordem gravada");
        for (String nome : List.of("ana", "bruno", "carla")) {
            List<String> doRemetente = ana.chat().mensagens(geral).stream().map(Mensagem::conteudo)
                    .filter(t -> t.startsWith(nome + "-")).toList();
            for (int i = 0; i < porCliente; i++) {
                assertEquals(nome + "-" + i, doRemetente.get(i), "ordem de envio de cada cliente preservada");
            }
        }
    }

    private MensagemRepository repoMensagens() {
        return new MensagemRepository(db);
    }
}
