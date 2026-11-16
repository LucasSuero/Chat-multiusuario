package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.service.ServicoChat;
import br.unicesumar.chat.testsupport.RepositoriosFalsos;
import br.unicesumar.chat.testsupport.SessaoFalsa;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** O protocolo inteiro (comando entra, respostas saem) exercitado em memória. */
class ProcessadorComandosTest {

    RepositoriosFalsos repos;
    ServicoChat servico;
    RegistroSessoes registro;
    ProcessadorComandos processador;
    Usuario ana;
    Usuario bruno;
    Usuario carla;

    @BeforeEach
    void preparar() {
        repos = new RepositoriosFalsos();
        servico = new ServicoChat(repos.usuarios, repos.conversas, repos.mensagens, Clock.systemUTC());
        registro = new RegistroSessoes();
        processador = new ProcessadorComandos(servico, new Roteador(servico, registro));
        ana = repos.usuarios.criar("ana", "h", "avatar01");
        bruno = repos.usuarios.criar("bruno", "h", "avatar02");
        carla = repos.usuarios.criar("carla", "h", "avatar03");
    }

    private SessaoFalsa logada(Usuario u) {
        SessaoFalsa s = new SessaoFalsa(u);
        registro.adicionar(s);
        return s;
    }

    private static String cmd(String nome, Object... campos) {
        return Protocolo.codificar(nome, campos);
    }

    private static String ultimo(SessaoFalsa s) {
        List<String> r = s.recebidas();
        return r.get(r.size() - 1);
    }

    @Test
    void semLoginSoRegistrarLoginESairSaoAceitos() {
        SessaoFalsa s = new SessaoFalsa();
        processador.processar(s, cmd("LISTAR_CONVERSAS"));
        processador.processar(s, cmd("MSG", 1, "oi"));
        processador.processar(s, cmd("ENTRAR", 1));
        assertEquals(3, s.recebidas("ERRO").size());
        assertTrue(s.recebidas().stream().allMatch(l -> l.startsWith("ERRO\tNAO_AUTENTICADO")));
    }

    @Test
    void registrarAutenticaASessaoEJaPermiteUsarOChat() {
        SessaoFalsa s = new SessaoFalsa();
        registro.adicionar(s);
        processador.processar(s, cmd("REGISTRAR", "diana", "segredo1", "avatar07"));
        assertEquals(1, s.recebidas("AUTENTICADO").size());
        assertEquals("diana", s.usuario().orElseThrow().username());
        processador.processar(s, cmd("LISTAR_CONVERSAS"));
        assertEquals("FIM_CONVERSAS", ultimo(s));
    }

    @Test
    void loginComSenhaErradaNaoAutentica() {
        servico.registrar("diana", "segredo1".toCharArray(), null);
        SessaoFalsa s = new SessaoFalsa();
        processador.processar(s, cmd("LOGIN", "diana", "errada"));
        assertTrue(ultimo(s).startsWith("ERRO\tCREDENCIAIS_INVALIDAS"));
        assertTrue(s.usuario().isEmpty());
        processador.processar(s, cmd("LOGIN", "diana", "segredo1"));
        assertEquals(1, s.recebidas("AUTENTICADO").size());
        processador.processar(s, cmd("LOGIN", "diana", "segredo1"));
        assertTrue(ultimo(s).startsWith("ERRO\tVALIDACAO"), "login duplo na mesma sessão é recusado");
    }

    @Test
    void entrarNaConversaEnviaHistoricoEntreMarcadores() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        servico.enviarMensagem(ana, conversa, "um");
        servico.enviarMensagem(bruno, conversa, "dois");
        servico.enviarMensagem(ana, conversa, "três");
        SessaoFalsa s = logada(bruno);
        processador.processar(s, cmd("ENTRAR", conversa));
        List<String> r = s.recebidas();
        assertEquals(5, r.size());
        assertEquals("HISTORICO_INICIO\t" + conversa, r.get(0));
        assertEquals(List.of("um", "dois", "três"), s.conteudos());
        assertEquals("HISTORICO_FIM\t" + conversa, r.get(4));
    }

    @Test
    void historicoDeConversaAlheiaEhNegadoESemVazamento() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        servico.enviarMensagem(ana, conversa, "particular");
        SessaoFalsa intruso = logada(carla);
        processador.processar(intruso, cmd("ENTRAR", conversa));
        assertEquals(1, intruso.recebidas().size());
        assertTrue(intruso.recebidas().get(0).startsWith("ERRO\tCONVERSA_INACESSIVEL"));
        assertTrue(intruso.conteudos().isEmpty());
    }

    @Test
    void mensagemEnviadaPeloProtocoloChegaAoOutroParticipante() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        SessaoFalsa sAna = logada(ana);
        SessaoFalsa sBruno = logada(bruno);
        SessaoFalsa sCarla = logada(carla);
        processador.processar(sAna, cmd("MSG", conversa, "linha 1\nlinha 2\tcom tab"));
        assertEquals(List.of("linha 1\nlinha 2\tcom tab"), sBruno.conteudos());
        List<String> campos = Protocolo.dividir(sBruno.recebidas("MSG").get(0));
        assertEquals(8, campos.size());
        assertEquals("ana", campos.get(4));
        assertTrue(sCarla.recebidas().isEmpty());
    }

    @Test
    void abrirPrivadaAvisaOOutroUsuarioComConversaNova() {
        SessaoFalsa sAna = logada(ana);
        SessaoFalsa sBruno = logada(bruno);
        processador.processar(sAna, cmd("ABRIR_PRIVADA", bruno.id()));
        assertTrue(ultimo(sAna).startsWith("CONVERSA\t"));
        String aviso = ultimo(sBruno);
        assertTrue(aviso.startsWith("CONVERSA_NOVA\t"));
        assertEquals("ana", Protocolo.dividir(aviso).get(6), "para o bruno, o parceiro é a ana");
    }

    @Test
    void criarGrupoAvisaOsIntegrantesEBuscaDeUsuariosFunciona() {
        SessaoFalsa sAna = logada(ana);
        SessaoFalsa sBruno = logada(bruno);
        SessaoFalsa sCarla = logada(carla);
        processador.processar(sAna, cmd("CRIAR_GRUPO", "Turma", "", bruno.id() + "," + carla.id()));
        assertTrue(ultimo(sAna).startsWith("CONVERSA\t"));
        assertTrue(ultimo(sBruno).startsWith("CONVERSA_NOVA\t"));
        assertTrue(ultimo(sCarla).startsWith("CONVERSA_NOVA\t"));
        processador.processar(sAna, cmd("BUSCAR_USUARIOS", "br"));
        assertEquals(1, sAna.recebidas("USUARIO").size());
        assertTrue(sAna.recebidas("USUARIO").get(0).contains("bruno"));
        assertEquals("FIM_USUARIOS", ultimo(sAna));
    }

    @Test
    void comandoInvalidoGeraErroEASessaoContinua() {
        SessaoFalsa s = logada(ana);
        assertTrue(processador.processar(s, "VOAR"));
        assertTrue(ultimo(s).startsWith("ERRO\tCOMANDO_INVALIDO"));
        assertTrue(processador.processar(s, cmd("ENTRAR", "abc")));
        assertTrue(ultimo(s).startsWith("ERRO\tCOMANDO_INVALIDO"));
        assertTrue(processador.processar(s, cmd("LISTAR_CONVERSAS")));
        assertEquals("FIM_CONVERSAS", ultimo(s));
    }

    @Test
    void sairResponderEPedirParaEncerrar() {
        SessaoFalsa s = logada(ana);
        assertFalse(processador.processar(s, cmd("SAIR")));
        assertTrue(ultimo(s).startsWith("OK\t"));
    }

    @Test
    void avatarPersisteNotificaAsSessoesDoUsuarioEAsProximasMensagensUsamOAvatarNovo() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        SessaoFalsa celular = logada(ana);
        SessaoFalsa notebook = logada(ana);
        SessaoFalsa sBruno = logada(bruno);
        processador.processar(celular, cmd("AVATAR", "avatar05"));
        assertTrue(ultimo(celular).startsWith("PERFIL_ATUALIZADO\t" + ana.id() + "\tana\tavatar05"));
        assertTrue(ultimo(notebook).startsWith("PERFIL_ATUALIZADO\t"), "as outras sessões da ana também são avisadas");
        assertEquals("avatar05", notebook.usuario().orElseThrow().avatar());
        assertEquals("avatar05", repos.usuarios.buscarPorId(ana.id()).orElseThrow().avatar());
        processador.processar(notebook, cmd("MSG", conversa, "oi"));
        assertEquals("avatar05", Protocolo.dividir(sBruno.recebidas("MSG").get(0)).get(5));
        assertTrue(sBruno.recebidas("PERFIL_ATUALIZADO").isEmpty());
    }

    @Test
    void avatarInvalidoGeraErroDeValidacao() {
        SessaoFalsa s = logada(ana);
        processador.processar(s, cmd("AVATAR", "<script>"));
        assertTrue(ultimo(s).startsWith("ERRO\tVALIDACAO"));
        assertEquals("avatar01", s.usuario().orElseThrow().avatar());
    }

    @Test
    void aoAutenticarOServidorEnviaSozinhoOHistoricoDaSalaGeral() {
        Usuario velho = servico.registrar("diana", "segredo1".toCharArray(), null);
        for (int i = 1; i <= 3; i++) {
            servico.enviarMensagem(velho, servico.idGeral(), "msg " + i);
        }
        SessaoFalsa nova = new SessaoFalsa();
        registro.adicionar(nova);
        processador.processar(nova, cmd("REGISTRAR", "edu", "segredo1", "avatar02"));
        List<String> r = nova.recebidas();
        assertEquals(6, r.size(), "AUTENTICADO + INICIO + 3 MSG + FIM, sem o cliente pedir nada");
        assertTrue(r.get(0).startsWith("AUTENTICADO\t"));
        assertEquals("HISTORICO_INICIO\t" + servico.idGeral(), r.get(1));
        assertEquals(List.of("msg 1", "msg 2", "msg 3"), nova.conteudos());
        assertEquals("HISTORICO_FIM\t" + servico.idGeral(), r.get(5));

        SessaoFalsa login = new SessaoFalsa();
        registro.adicionar(login);
        processador.processar(login, cmd("LOGIN", "diana", "segredo1"));
        assertEquals(List.of("msg 1", "msg 2", "msg 3"), login.conteudos(), "o login também recebe o histórico");
    }

    @Test
    void historicoDaGeralVemVazioQuandoAindaNaoHaMensagens() {
        SessaoFalsa s = new SessaoFalsa();
        registro.adicionar(s);
        processador.processar(s, cmd("REGISTRAR", "diana", "segredo1", "avatar01"));
        assertEquals(3, s.recebidas().size());
        assertEquals("HISTORICO_INICIO", Protocolo.dividir(s.recebidas().get(1)).get(0));
        assertEquals("HISTORICO_FIM", Protocolo.dividir(s.recebidas().get(2)).get(0));
    }

    @Test
    void mensagemNaSalaGeralChegaATodosOsDemaisConectadosEPrivadaNao() {
        SessaoFalsa sAna = new SessaoFalsa();
        SessaoFalsa sBruno = new SessaoFalsa();
        SessaoFalsa sCarla = new SessaoFalsa();
        SessaoFalsa anonima = new SessaoFalsa();
        for (SessaoFalsa s : List.of(sAna, sBruno, sCarla, anonima)) {
            registro.adicionar(s);
        }
        processador.processar(sAna, cmd("REGISTRAR", "ana2", "segredo1", "avatar01"));
        processador.processar(sBruno, cmd("REGISTRAR", "bruno2", "segredo1", "avatar02"));
        processador.processar(sCarla, cmd("REGISTRAR", "carla2", "segredo1", "avatar03"));
        long geral = servico.idGeral();

        processador.processar(sAna, cmd("MSG", geral, "olá, todos"));
        assertEquals(List.of("olá, todos"), sBruno.conteudos());
        assertEquals(List.of("olá, todos"), sCarla.conteudos());
        assertEquals(List.of("olá, todos"), sAna.conteudos(), "o remetente recebe o eco");
        assertTrue(anonima.recebidas().isEmpty(), "quem ainda não se autenticou não recebe");

        long privada = servico.abrirPrivada(sAna.usuario().orElseThrow(), sBruno.usuario().orElseThrow().id()).conversa().id();
        processador.processar(sAna, cmd("MSG", privada, "só entre nós"));
        assertEquals(2, sBruno.conteudos().size());
        assertEquals(1, sCarla.conteudos().size(), "a conversa privada não vaza para a carla nem para a sala Geral");
    }
}
