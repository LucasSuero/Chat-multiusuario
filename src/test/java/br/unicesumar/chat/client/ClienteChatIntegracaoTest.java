package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.server.ProcessadorComandos;
import br.unicesumar.chat.server.RegistroSessoes;
import br.unicesumar.chat.server.Roteador;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.service.ServicoChat;
import br.unicesumar.chat.testsupport.RepositoriosFalsos;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ClienteChat (sem JavaFX) conversando com o Servidor REAL por sockets reais em localhost.
 * Só o banco é substituído por repositórios em memória.
 */
class ClienteChatIntegracaoTest {

    /** Ouvinte que guarda cada evento em uma fila para o teste esperar. */
    static final class Gravador implements ClienteChat.Ouvinte {
        final BlockingQueue<Usuario> autenticados = new LinkedBlockingQueue<>();
        final BlockingQueue<Usuario> perfis = new LinkedBlockingQueue<>();
        final BlockingQueue<ConversaVisao> conversas = new LinkedBlockingQueue<>();
        final BlockingQueue<ConversaVisao> conversasNovas = new LinkedBlockingQueue<>();
        final BlockingQueue<List<ConversaVisao>> listagens = new LinkedBlockingQueue<>();
        final BlockingQueue<Mensagem> mensagens = new LinkedBlockingQueue<>();
        final BlockingQueue<Long> historicos = new LinkedBlockingQueue<>();
        final BlockingQueue<List<Usuario>> buscas = new LinkedBlockingQueue<>();
        final BlockingQueue<List<Usuario>> participantes = new LinkedBlockingQueue<>();
        final BlockingQueue<String> erros = new LinkedBlockingQueue<>();
        final BlockingQueue<String> desconexoes = new LinkedBlockingQueue<>();

        public void onAutenticado(Usuario u) { autenticados.add(u); }
        public void onPerfilAtualizado(Usuario u) { perfis.add(u); }
        public void onConversa(ConversaVisao c, boolean nova) { (nova ? conversasNovas : conversas).add(c); }
        public void onConversasCarregadas(List<ConversaVisao> c) { listagens.add(c); }
        public void onHistoricoCarregado(long id) { historicos.add(id); }
        public void onMensagem(Mensagem m) { mensagens.add(m); }
        public void onUsuariosEncontrados(String termo, List<Usuario> u) { buscas.add(u); }
        public void onParticipantes(long id, List<Usuario> u) { participantes.add(u); }
        public void onErro(String codigo, String texto) { erros.add(codigo); }
        public void onDesconectado(String motivo) { desconexoes.add(motivo); }

        static <T> T esperar(BlockingQueue<T> fila) throws InterruptedException {
            T v = fila.poll(5, TimeUnit.SECONDS);
            assertNotNull(v, "evento esperado não chegou em 5 s");
            return v;
        }

        static boolean nadaChega(BlockingQueue<?> fila, long ms) throws InterruptedException {
            return fila.poll(ms, TimeUnit.MILLISECONDS) == null;
        }
    }

    RepositoriosFalsos repos;
    RegistroSessoes registro;
    ProcessadorComandos processador;
    Servidor servidor;
    final List<ClienteChat> clientes = new ArrayList<>();

    @BeforeEach
    void iniciar() throws IOException {
        repos = new RepositoriosFalsos();
        ServicoChat servico = new ServicoChat(repos.usuarios, repos.conversas, repos.mensagens, Clock.systemUTC());
        registro = new RegistroSessoes();
        processador = new ProcessadorComandos(servico, new Roteador(servico, registro));
        servidor = new Servidor(0, processador, registro);
        servidor.iniciar();
    }

    @AfterEach
    void encerrar() {
        clientes.forEach(ClienteChat::sair);
        servidor.parar();
    }

    /** Cria um cliente já conectado e cadastrado, devolvendo o gravador de eventos. */
    private Gravador cadastrar(ClienteChat cliente, String nome, String avatar) throws Exception {
        Gravador g = new Gravador();
        cliente.definirOuvinte(g);
        cliente.conectar("localhost", servidor.porta());
        clientes.add(cliente);
        cliente.registrar(nome, "segredo1", avatar);
        Gravador.esperar(g.autenticados);
        return g;
    }

    @Test
    void doisClientesConversamEmPrivadoESoOParticipanteRecebe() throws Exception {
        ClienteChat ana = new ClienteChat();
        ClienteChat bruno = new ClienteChat();
        ClienteChat carla = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar02");
        Gravador gBruno = cadastrar(bruno, "bruno", "avatar03");
        Gravador gCarla = cadastrar(carla, "carla", "avatar04");

        ana.buscarUsuarios("bru");
        List<Usuario> achados = Gravador.esperar(gAna.buscas);
        assertEquals(1, achados.size());
        assertEquals("bruno", achados.get(0).username());

        ana.abrirPrivada(achados.get(0).id());
        ConversaVisao conversaAna = Gravador.esperar(gAna.conversas);
        ConversaVisao conversaBruno = Gravador.esperar(gBruno.conversasNovas);
        assertEquals("bruno", conversaAna.parceiro().username());
        assertEquals("ana", conversaBruno.parceiro().username());
        long id = conversaAna.conversa().id();

        ana.enviarMensagem(id, "  oi bruno  ");
        Mensagem recebida = Gravador.esperar(gBruno.mensagens);
        assertEquals("oi bruno", recebida.conteudo());
        assertEquals("ana", recebida.remetente().username());
        assertEquals("avatar02", recebida.remetente().avatar());
        assertEquals(id, recebida.conversaId());
        assertEquals("oi bruno", Gravador.esperar(gAna.mensagens).conteudo(), "o remetente recebe o eco do servidor");
        assertTrue(Gravador.nadaChega(gCarla.mensagens, 300), "carla não participa da conversa privada");

        bruno.enviarMensagem(id, "oi ana");
        assertEquals("oi ana", Gravador.esperar(gAna.mensagens).conteudo());
        assertEquals(2, ana.mensagens(id).size());
        assertEquals("oi bruno", ana.mensagens(id).get(0).conteudo());
    }

    @Test
    void mensagensDeGrupoChegamATodosOsIntegrantes() throws Exception {
        ClienteChat ana = new ClienteChat();
        ClienteChat bruno = new ClienteChat();
        ClienteChat carla = new ClienteChat();
        ClienteChat diego = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar01");
        Gravador gBruno = cadastrar(bruno, "bruno", "avatar02");
        Gravador gCarla = cadastrar(carla, "carla", "avatar03");
        Gravador gDiego = cadastrar(diego, "diego", "avatar04");
        long brunoId = bruno.usuario().orElseThrow().id();
        long carlaId = carla.usuario().orElseThrow().id();

        ana.criarGrupo("Turma POO", "grupo02", List.of(brunoId, carlaId));
        ConversaVisao grupo = Gravador.esperar(gAna.conversas);
        assertEquals("Turma POO", grupo.conversa().nome());
        assertEquals("grupo02", grupo.conversa().imagem());
        Gravador.esperar(gBruno.conversasNovas);
        Gravador.esperar(gCarla.conversasNovas);
        assertTrue(Gravador.nadaChega(gDiego.conversasNovas, 300), "quem não é integrante não é avisado");

        carla.enviarMensagem(grupo.conversa().id(), "olá, turma!");
        assertEquals("olá, turma!", Gravador.esperar(gAna.mensagens).conteudo());
        assertEquals("olá, turma!", Gravador.esperar(gBruno.mensagens).conteudo());
        assertEquals("olá, turma!", Gravador.esperar(gCarla.mensagens).conteudo());
        assertTrue(Gravador.nadaChega(gDiego.mensagens, 300), "diego não participa do grupo");

        ana.participantes(grupo.conversa().id());
        assertEquals(3, Gravador.esperar(gAna.participantes).size());
        diego.participantes(grupo.conversa().id());
        assertEquals("CONVERSA_INACESSIVEL", Gravador.esperar(gDiego.erros));
    }

    @Test
    void historicoVemDoServidorEhOrdenadoEnaoDuplica() throws Exception {
        ClienteChat ana = new ClienteChat();
        ClienteChat bruno = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar01");
        Gravador gBruno = cadastrar(bruno, "bruno", "avatar02");
        ana.abrirPrivada(bruno.usuario().orElseThrow().id());
        long id = Gravador.esperar(gAna.conversas).conversa().id();
        for (String t : List.of("um", "dois", "três")) {
            ana.enviarMensagem(id, t);
            Gravador.esperar(gAna.mensagens);
        }
        bruno.sair();

        ClienteChat brunoDeNovo = new ClienteChat();
        Gravador g = new Gravador();
        brunoDeNovo.definirOuvinte(g);
        brunoDeNovo.conectar("localhost", servidor.porta());
        clientes.add(brunoDeNovo);
        brunoDeNovo.login("bruno", "segredo1");
        Gravador.esperar(g.autenticados);
        long historicoDoLogin = Gravador.esperar(g.historicos); // enviado pelo servidor no login, sem ninguém pedir
        brunoDeNovo.carregarConversas();
        List<ConversaVisao> lista = Gravador.esperar(g.listagens);
        assertEquals(2, lista.size(), "sala Geral + a conversa privada");
        assertTrue(lista.stream().anyMatch(v -> v.conversa().id() == id));
        long idGeral = lista.stream().filter(v -> "Geral".equals(v.conversa().nome())).findFirst().orElseThrow().conversa().id();
        assertEquals(idGeral, historicoDoLogin, "o histórico enviado no login é o da sala Geral");
        assertNotEquals(id, historicoDoLogin, "e não o da conversa privada, que só vem quando o cliente a abre");

        brunoDeNovo.entrarNaConversa(id);
        assertEquals(id, (long) Gravador.esperar(g.historicos));
        assertEquals(List.of("um", "dois", "três"),
                brunoDeNovo.mensagens(id).stream().map(Mensagem::conteudo).toList());
        assertEquals("um", Gravador.esperar(g.mensagens).conteudo());

        brunoDeNovo.entrarNaConversa(id);
        Gravador.esperar(g.historicos);
        assertEquals(3, brunoDeNovo.mensagens(id).size(), "entrar de novo não duplica mensagens");
    }

    @Test
    void trocaDeAvatarEhGravadaEPermaneceAposSairEEntrarDeNovo() throws Exception {
        ClienteChat ana = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar01");
        ana.alterarAvatar("avatar07");
        assertEquals("avatar07", Gravador.esperar(gAna.perfis).avatar());
        assertEquals("avatar07", ana.usuario().orElseThrow().avatar());
        ana.alterarAvatar("avatar99; DROP TABLE");
        assertEquals("VALIDACAO", Gravador.esperar(gAna.erros));
        ana.sair();

        ClienteChat denovo = new ClienteChat();
        Gravador g = new Gravador();
        denovo.definirOuvinte(g);
        denovo.conectar("localhost", servidor.porta());
        clientes.add(denovo);
        denovo.login("ana", "segredo1");
        assertEquals("avatar07", Gravador.esperar(g.autenticados).avatar());
    }

    @Test
    void loginInvalidoGeraErroEASessaoNaoAutentica() throws Exception {
        ClienteChat ana = new ClienteChat();
        cadastrar(ana, "ana", "avatar01");
        ClienteChat intruso = new ClienteChat();
        Gravador g = new Gravador();
        intruso.definirOuvinte(g);
        intruso.conectar("localhost", servidor.porta());
        clientes.add(intruso);
        intruso.login("ana", "senha-errada");
        assertEquals("CREDENCIAIS_INVALIDAS", Gravador.esperar(g.erros));
        assertFalse(intruso.autenticado());
        intruso.registrar("ana", "segredo1", "avatar01");
        assertEquals("USUARIO_EXISTE", Gravador.esperar(g.erros));
        intruso.registrar("x", "123", "avatar01");
        assertEquals("VALIDACAO", Gravador.esperar(g.erros));
        intruso.carregarConversas();
        assertEquals("NAO_AUTENTICADO", Gravador.esperar(g.erros));
    }

    @Test
    void quedaDoServidorEhDetectadaEReconectarRestauraASessao() throws Exception {
        ClienteChat ana = new ClienteChat();
        Gravador g = cadastrar(ana, "ana", "avatar05");
        int porta = servidor.porta();

        servidor.parar();
        String motivo = Gravador.esperar(g.desconexoes);
        assertFalse(motivo.isBlank());
        assertFalse(ana.conectado());
        ana.carregarConversas();
        assertEquals("SEM_CONEXAO", Gravador.esperar(g.erros), "agir sem conexão avisa em vez de lançar exceção");
        assertThrows(IOException.class, () -> ana.reconectar(), "com o servidor fora do ar, reconectar falha");

        servidor = new Servidor(porta, processador, registro);
        servidor.iniciar();
        ana.reconectar();
        assertEquals("avatar05", Gravador.esperar(g.autenticados).avatar());
        assertTrue(ana.conectado());
        ana.carregarConversas();
        Gravador.esperar(g.listagens);
    }

    @Test
    void sairNaoDisparaDesconexaoEEsquecePrivacidade() throws Exception {
        ClienteChat ana = new ClienteChat();
        Gravador g = cadastrar(ana, "ana", "avatar01");
        ana.sair();
        assertTrue(Gravador.nadaChega(g.desconexoes, 400), "sair por vontade própria não é queda de conexão");
        assertTrue(ana.usuario().isEmpty());
        assertFalse(ana.conectado());
        assertThrows(IllegalStateException.class, () -> ana.reconectar(), "sair apaga as credenciais da memória");
        for (int i = 0; i < 40 && registro.total() > 0; i++) {
            Thread.sleep(50);
        }
        assertEquals(0, registro.total(), "o servidor registra a saída");
    }

    @Test
    void mensagemVaziaNaoVaiAoServidorEMuitoGrandeEhRecusadaNoCliente() throws Exception {
        ClienteChat ana = new ClienteChat();
        ClienteChat bruno = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar01");
        cadastrar(bruno, "bruno", "avatar02");
        ana.abrirPrivada(bruno.usuario().orElseThrow().id());
        long id = Gravador.esperar(gAna.conversas).conversa().id();
        ana.enviarMensagem(id, "   ");
        ana.enviarMensagem(id, "x".repeat(Mensagem.TAMANHO_MAXIMO + 1));
        assertEquals("VALIDACAO", Gravador.esperar(gAna.erros));
        assertTrue(Gravador.nadaChega(gAna.mensagens, 300));
        assertEquals(0, repos.mensagens.total());
    }

    @Test
    void conectarEmPortaFechadaLancaIOException() throws Exception {
        int portaLivre;
        try (ServerSocket s = new ServerSocket(0)) {
            portaLivre = s.getLocalPort();
        }
        ClienteChat c = new ClienteChat();
        int p = portaLivre;
        assertThrows(IOException.class, () -> c.conectar("localhost", p));
        assertFalse(c.conectado());
    }

    @Test
    void acentosEEmojisSaoPreservadosDeUmClienteAOutro() throws Exception {
        ClienteChat ana = new ClienteChat();
        ClienteChat bruno = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar01");
        Gravador gBruno = cadastrar(bruno, "bruno", "avatar02");
        ana.abrirPrivada(bruno.usuario().orElseThrow().id());
        long id = Gravador.esperar(gAna.conversas).conversa().id();
        String texto = "açaí, coração ✓ 😀\nsegunda linha\ttab";
        ana.enviarMensagem(id, texto);
        assertEquals(texto, Gravador.esperar(gBruno.mensagens).conteudo());
    }

    @Test
    void salaGeralEhBroadcastEHistoricoChegaNoLoginSemPedirNada() throws Exception {
        ClienteChat ana = new ClienteChat();
        ClienteChat bruno = new ClienteChat();
        ClienteChat carla = new ClienteChat();
        Gravador gAna = cadastrar(ana, "ana", "avatar01");
        Gravador gBruno = cadastrar(bruno, "bruno", "avatar02");
        Gravador gCarla = cadastrar(carla, "carla", "avatar03");
        long geral = Gravador.esperar(gAna.historicos);
        Gravador.esperar(gBruno.historicos);
        Gravador.esperar(gCarla.historicos);

        ana.enviarMensagem(geral, "olá, sala geral");
        assertEquals("olá, sala geral", Gravador.esperar(gBruno.mensagens).conteudo());
        assertEquals("olá, sala geral", Gravador.esperar(gCarla.mensagens).conteudo());
        assertEquals("olá, sala geral", Gravador.esperar(gAna.mensagens).conteudo());

        bruno.sair();
        ClienteChat diego = new ClienteChat();
        Gravador gDiego = cadastrar(diego, "diego", "avatar04");
        assertEquals(geral, (long) Gravador.esperar(gDiego.historicos));
        assertEquals(1, diego.mensagens(geral).size(), "o histórico da Geral chegou sozinho, sem entrarNaConversa");
        assertEquals("olá, sala geral", diego.mensagens(geral).get(0).conteudo());
        assertEquals("olá, sala geral", Gravador.esperar(gDiego.mensagens).conteudo());

        diego.carregarConversas();
        List<ConversaVisao> lista = Gravador.esperar(gDiego.listagens);
        assertEquals("Geral", lista.get(0).conversa().nome());
    }

    @Test
    void usuarioNaoPodeCriarGrupoComONomeReservadoGeral() throws Exception {
        ClienteChat ana = new ClienteChat();
        Gravador g = cadastrar(ana, "ana", "avatar01");
        ana.criarGrupo("geral", "grupo02", List.of());
        assertEquals("VALIDACAO", Gravador.esperar(g.erros));
    }
}
