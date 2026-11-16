package br.unicesumar.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.testsupport.RepositoriosFalsos;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicoChatTest {

    RepositoriosFalsos repos;
    ServicoChat servico;
    Usuario ana;
    Usuario bruno;
    Usuario carla;

    @BeforeEach
    void preparar() {
        repos = new RepositoriosFalsos();
        servico = new ServicoChat(repos.usuarios, repos.conversas, repos.mensagens,
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC));
        ana = repos.usuarios.criar("ana", "h", "avatar01");
        bruno = repos.usuarios.criar("bruno", "h", "avatar02");
        carla = repos.usuarios.criar("carla", "h", "avatar03");
    }

    @Test
    void cadastroGuardaSomenteHashELoginFunciona() {
        Usuario nova = servico.registrar("diana", "segredo1".toCharArray(), "avatar05");
        assertTrue(repos.usuarios.hashDe(nova.id()).startsWith("pbkdf2$"));
        assertFalse(repos.usuarios.hashDe(nova.id()).contains("segredo1"));
        assertEquals(nova, servico.autenticar("diana", "segredo1".toCharArray()));
    }

    @Test
    void loginComSenhaErradaOuUsuarioInexistenteDaMesmoErro() {
        servico.registrar("diana", "segredo1".toCharArray(), null);
        ChatException senhaErrada = assertThrows(ChatException.class,
                () -> servico.autenticar("diana", "errada".toCharArray()));
        ChatException inexistente = assertThrows(ChatException.class,
                () -> servico.autenticar("fantasma", "segredo1".toCharArray()));
        assertEquals(Erro.CREDENCIAIS_INVALIDAS, senhaErrada.erro());
        assertEquals(senhaErrada.getMessage(), inexistente.getMessage());
    }

    @Test
    void usernameDuplicadoEhRecusado() {
        ChatException e = assertThrows(ChatException.class,
                () -> servico.registrar("ANA", "segredo1".toCharArray(), null));
        assertEquals(Erro.USUARIO_EXISTE, e.erro());
    }

    @Test
    void participanteEnviaESuaMensagemFicaNoHistorico() {
        ConversaVisao v = servico.abrirPrivada(ana, bruno.id());
        Mensagem m = servico.enviarMensagem(ana, v.conversa().id(), "  oi, bruno  ");
        assertTrue(m.id() > 0);
        assertEquals("oi, bruno", m.conteudo());
        assertEquals(Instant.parse("2026-11-10T12:00:00Z"), m.enviadaEm());
        List<Mensagem> historico = servico.entrarNaConversa(bruno, v.conversa().id());
        assertEquals(1, historico.size());
        assertEquals("ana", historico.get(0).remetente().username());
    }

    @Test
    void quemNaoParticipaNaoEnviaNemLeHistoricoNemVeParticipantes() {
        long conversaId = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        servico.enviarMensagem(ana, conversaId, "segredo");
        assertEquals(Erro.CONVERSA_INACESSIVEL,
                assertThrows(ChatException.class, () -> servico.enviarMensagem(carla, conversaId, "intruso")).erro());
        assertEquals(Erro.CONVERSA_INACESSIVEL,
                assertThrows(ChatException.class, () -> servico.entrarNaConversa(carla, conversaId)).erro());
        assertEquals(Erro.CONVERSA_INACESSIVEL,
                assertThrows(ChatException.class, () -> servico.participantes(carla, conversaId)).erro());
        assertEquals(1, repos.mensagens.total());
    }

    @Test
    void conversaInexistenteTemMesmaRespostaDeConversaAlheia() {
        ChatException e = assertThrows(ChatException.class, () -> servico.entrarNaConversa(ana, 999));
        assertEquals(Erro.CONVERSA_INACESSIVEL, e.erro());
    }

    @Test
    void historicoTrazSomenteAsUltimasMensagensEmOrdem() {
        long id = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        for (int i = 1; i <= ServicoChat.HISTORICO_PADRAO + 5; i++) {
            servico.enviarMensagem(i % 2 == 0 ? bruno : ana, id, "msg " + i);
        }
        List<Mensagem> historico = servico.entrarNaConversa(ana, id);
        assertEquals(ServicoChat.HISTORICO_PADRAO, historico.size());
        assertEquals("msg 6", historico.get(0).conteudo());
        assertEquals("msg " + (ServicoChat.HISTORICO_PADRAO + 5), historico.get(historico.size() - 1).conteudo());
    }

    @Test
    void mensagemVaziaOuGrandeDemaisViraErroDeValidacao() {
        long id = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        assertThrows(ValidacaoException.class, () -> servico.enviarMensagem(ana, id, "   "));
        assertThrows(ValidacaoException.class, () -> servico.enviarMensagem(ana, id, "x".repeat(2001)));
        assertEquals(0, repos.mensagens.total());
    }

    @Test
    void conversaPrivadaComSiMesmoOuComUsuarioInexistenteEhRecusada() {
        assertThrows(ValidacaoException.class, () -> servico.abrirPrivada(ana, ana.id()));
        assertEquals(Erro.NAO_ENCONTRADO,
                assertThrows(ChatException.class, () -> servico.abrirPrivada(ana, 999)).erro());
    }

    @Test
    void grupoComControleDeParticipantes() {
        ConversaVisao v = servico.criarGrupo(ana, " Turma POO ", "", List.of(bruno.id()));
        Conversa grupo = v.conversa();
        assertEquals("Turma POO", grupo.nome());
        assertNull(grupo.imagem());
        assertEquals(2, servico.participantes(ana, grupo.id()).size());
        assertEquals(Erro.CONVERSA_INACESSIVEL,
                assertThrows(ChatException.class, () -> servico.adicionarParticipante(carla, grupo.id(), carla.id())).erro());
        servico.adicionarParticipante(bruno, grupo.id(), carla.id());
        assertTrue(servico.idsParticipantes(grupo.id()).contains(carla.id()));
    }

    @Test
    void naoSeAdicionaParticipanteEmConversaPrivadaNemUsuarioInexistente() {
        long privada = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        assertThrows(ValidacaoException.class, () -> servico.adicionarParticipante(ana, privada, carla.id()));
        long grupo = servico.criarGrupo(ana, "G", null, List.of()).conversa().id();
        assertEquals(Erro.NAO_ENCONTRADO,
                assertThrows(ChatException.class, () -> servico.adicionarParticipante(ana, grupo, 999)).erro());
        assertEquals(Erro.NAO_ENCONTRADO,
                assertThrows(ChatException.class, () -> servico.criarGrupo(ana, "G2", null, List.of(999L))).erro());
    }

    @Test
    void listaDeConversasMostraOParceiroDasPrivadas() {
        servico.abrirPrivada(ana, bruno.id());
        servico.criarGrupo(ana, "Grupo", null, List.of(carla.id()));
        List<ConversaVisao> conversas = servico.conversasDoUsuario(bruno);
        assertEquals(1, conversas.size());
        assertEquals("ana", conversas.get(0).parceiro().username());
        assertEquals(2, servico.conversasDoUsuario(ana).size());
    }

    @Test
    void trocaDeAvatarEhGravadaEVoltaNoProximoLogin() {
        Usuario nova = servico.registrar("diana", "segredo1".toCharArray(), "avatar01");
        Usuario atualizada = servico.atualizarAvatar(nova, "avatar09");
        assertEquals("avatar09", atualizada.avatar());
        assertEquals("avatar09", servico.autenticar("diana", "segredo1".toCharArray()).avatar());
    }

    @Test
    void avatarInvalidoOuVazioNaoAlteraNada() {
        assertThrows(ValidacaoException.class, () -> servico.atualizarAvatar(ana, "  "));
        assertThrows(ValidacaoException.class, () -> servico.atualizarAvatar(ana, "../x"));
        assertEquals("avatar01", repos.usuarios.buscarPorId(ana.id()).orElseThrow().avatar());
    }

    @Test
    void mensagemEnviadaDepoisDaTrocaTrazOAvatarNovoNoHistorico() {
        long id = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        Usuario anaNova = servico.atualizarAvatar(ana, "avatar11");
        servico.enviarMensagem(anaNova, id, "com avatar novo");
        assertEquals("avatar11", servico.entrarNaConversa(bruno, id).get(0).remetente().avatar());
    }

    @Test
    void cadastroEntraAutomaticamenteNaSalaGeral() {
        Usuario nova = servico.registrar("diana", "segredo1".toCharArray(), null);
        assertTrue(servico.idsParticipantes(servico.idGeral()).contains(nova.id()));
        List<ConversaVisao> conversas = servico.conversasDoUsuario(nova);
        assertEquals(1, conversas.size());
        assertEquals(Conversa.NOME_GERAL, conversas.get(0).conversa().nome());
    }

    @Test
    void loginTambemIncluiNaGeralUsuariosCriadosAntesDelaExistir() {
        Usuario antiga = repos.usuarios.criar("eva", br.unicesumar.chat.security.PasswordHasher.gerar("segredo1".toCharArray()), "avatar01");
        assertFalse(servico.idsParticipantes(servico.idGeral()).contains(antiga.id()));
        servico.autenticar("eva", "segredo1".toCharArray());
        assertTrue(servico.idsParticipantes(servico.idGeral()).contains(antiga.id()));
        servico.autenticar("eva", "segredo1".toCharArray());
        assertEquals(1, servico.idsParticipantes(servico.idGeral()).stream().filter(i -> i == antiga.id()).count());
    }

    @Test
    void salaGeralTemIdEstavelEReuneTodosOsUsuariosCadastrados() {
        long id = servico.idGeral();
        assertEquals(id, servico.idGeral());
        Usuario a = servico.registrar("diana", "segredo1".toCharArray(), null);
        Usuario b = servico.registrar("edu", "segredo1".toCharArray(), null);
        assertEquals(java.util.Set.of(a.id(), b.id()), servico.idsParticipantes(id));
    }

    @Test
    void mensagemNaGeralFicaNoHistoricoDeQualquerUsuario() {
        Usuario a = servico.registrar("diana", "segredo1".toCharArray(), null);
        Usuario b = servico.registrar("edu", "segredo1".toCharArray(), null);
        servico.enviarMensagem(a, servico.idGeral(), "para todos");
        List<Mensagem> historico = servico.entrarNaConversa(b, servico.idGeral());
        assertEquals(1, historico.size());
        assertEquals("para todos", historico.get(0).conteudo());
        assertEquals("diana", historico.get(0).remetente().username());
    }

    @Test
    void nomeGeralEhReservadoEmQualquerCapitalizacao() {
        assertThrows(ValidacaoException.class, () -> servico.criarGrupo(ana, "Geral", null, List.of()));
        assertThrows(ValidacaoException.class, () -> servico.criarGrupo(ana, " geral ", null, List.of()));
        assertThrows(ValidacaoException.class, () -> servico.criarGrupo(ana, "GERAL", null, List.of()));
        assertDoesNotThrow(() -> servico.criarGrupo(ana, "Geral 2", null, List.of()));
    }

    @Test
    void historicoGeralTrazSoAsUltimasMensagensDaGeralEmOrdemSemMisturarPrivadas() {
        Usuario a = servico.registrar("diana", "segredo1".toCharArray(), null);
        Usuario b = servico.registrar("edu", "segredo1".toCharArray(), null);
        long privada = servico.abrirPrivada(a, b.id()).conversa().id();
        for (int i = 1; i <= ServicoChat.HISTORICO_PADRAO + 3; i++) {
            servico.enviarMensagem(i % 2 == 0 ? b : a, servico.idGeral(), "geral " + i);
        }
        servico.enviarMensagem(a, privada, "privada");
        List<Mensagem> h = servico.historicoGeral(b);
        assertEquals(ServicoChat.HISTORICO_PADRAO, h.size());
        assertEquals("geral 4", h.get(0).conteudo());
        assertEquals("geral " + (ServicoChat.HISTORICO_PADRAO + 3), h.get(h.size() - 1).conteudo());
        assertTrue(h.stream().noneMatch(m -> m.conteudo().equals("privada")));
    }
}
