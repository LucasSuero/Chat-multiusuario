package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoConversa;
import br.unicesumar.chat.model.Usuario;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testes de integração com SQLite em arquivo temporário (sem rede). */
class RepositoriosTest {

    @TempDir
    Path dir;

    Database db;
    UsuarioRepository usuarios;
    ConversaRepository conversas;
    MensagemRepository mensagens;

    @BeforeEach
    void preparar() {
        db = Database.emArquivo(dir.resolve("teste.db"));
        db.inicializar();
        usuarios = new UsuarioRepository(db);
        conversas = new ConversaRepository(db);
        mensagens = new MensagemRepository(db);
    }

    @Test
    void usernameDuplicadoIgnoraMaiusculas() {
        usuarios.criar("Ana", "hash", "avatar01");
        assertThrows(UsuarioDuplicadoException.class, () -> usuarios.criar("ana", "hash", "avatar01"));
    }

    @Test
    void pesquisaExcluiOProprioUsuario() {
        Usuario ana = usuarios.criar("ana", "h", "avatar01");
        usuarios.criar("anabela", "h", "avatar02");
        usuarios.criar("bruno", "h", "avatar03");
        List<Usuario> achados = usuarios.pesquisar("ana", ana.id());
        assertEquals(1, achados.size());
        assertEquals("anabela", achados.get(0).username());
    }

    @Test
    void conversaPrivadaEhReaproveitadaIndependenteDaOrdem() {
        Usuario a = usuarios.criar("ana", "h", "avatar01");
        Usuario b = usuarios.criar("bruno", "h", "avatar02");
        Conversa c1 = conversas.obterOuCriarPrivada(a.id(), b.id());
        Conversa c2 = conversas.obterOuCriarPrivada(b.id(), a.id());
        assertEquals(c1.id(), c2.id());
        assertEquals(TipoConversa.PRIVADA, c1.tipo());
    }

    @Test
    void grupoIncluiCriadorEControlaAcesso() {
        Usuario a = usuarios.criar("ana", "h", "avatar01");
        Usuario b = usuarios.criar("bruno", "h", "avatar02");
        Usuario c = usuarios.criar("carla", "h", "avatar03");
        Conversa g = conversas.criarGrupo("Turma", null, a.id(), List.of(b.id()));
        assertTrue(conversas.ehParticipante(g.id(), a.id()));
        assertTrue(conversas.ehParticipante(g.id(), b.id()));
        assertFalse(conversas.ehParticipante(g.id(), c.id()));
        conversas.adicionarParticipante(g.id(), c.id());
        assertEquals(3, conversas.listarParticipantes(g.id()).size());
    }

    @Test
    void historicoVoltaEmOrdemCronologicaELimitado() {
        Usuario a = usuarios.criar("ana", "h", "avatar01");
        Usuario b = usuarios.criar("bruno", "h", "avatar02");
        Conversa conv = conversas.obterOuCriarPrivada(a.id(), b.id());
        for (int i = 1; i <= 5; i++) {
            mensagens.salvar(Mensagem.nova(conv.id(), i % 2 == 0 ? b : a, "msg " + i, Instant.now()));
        }
        List<Mensagem> ultimas = mensagens.buscarUltimas(conv.id(), 3);
        assertEquals(List.of("msg 3", "msg 4", "msg 5"), ultimas.stream().map(Mensagem::conteudo).toList());
        assertEquals("bruno", ultimas.get(1).remetente().username());
        assertTrue(ultimas.get(0).id() > 0);
    }

    @Test
    void historicoNaoVazaEntreConversas() {
        Usuario a = usuarios.criar("ana", "h", "avatar01");
        Usuario b = usuarios.criar("bruno", "h", "avatar02");
        Usuario c = usuarios.criar("carla", "h", "avatar03");
        Conversa ab = conversas.obterOuCriarPrivada(a.id(), b.id());
        Conversa ac = conversas.obterOuCriarPrivada(a.id(), c.id());
        mensagens.salvar(Mensagem.nova(ab.id(), a, "só para o bruno", Instant.now()));
        assertTrue(mensagens.buscarUltimas(ac.id(), 10).isEmpty());
    }

    @Test
    void atualizarAvatarPersisteNoBanco() {
        Usuario ana = usuarios.criar("ana", "h", "avatar01");
        usuarios.atualizarAvatar(ana.id(), "avatar08");
        assertEquals("avatar08", usuarios.buscarPorId(ana.id()).orElseThrow().avatar());
        assertEquals("avatar08", usuarios.buscarCredencial("ana").orElseThrow().usuario().avatar());
    }

    @Test
    void salaGeralEhSemeadaUmaUnicaVezECompartilhada() {
        Conversa g1 = conversas.obterOuCriarGeral();
        db.inicializar(); // rodar o script de novo não pode duplicar a sala
        Conversa g2 = conversas.obterOuCriarGeral();
        assertEquals(g1.id(), g2.id());
        assertEquals("Geral", g1.nome());
        assertEquals(TipoConversa.GRUPO, g1.tipo());
        assertNull(g1.criadorId());
    }

    @Test
    void grupoDeUsuarioChamadoGeralNaoSeConfundeComASalaGeral() {
        Usuario ana = usuarios.criar("ana", "h", "avatar01");
        Conversa falso = conversas.criarGrupo("Geral", null, ana.id(), List.of());
        assertNotEquals(falso.id(), conversas.obterOuCriarGeral().id());
    }

    @Test
    void buscarUltimasDoPdfTrazOHistoricoDaSalaGeralEmOrdem() {
        Usuario a = usuarios.criar("ana", "h", "avatar01");
        Usuario b = usuarios.criar("bruno", "h", "avatar02");
        long geral = conversas.obterOuCriarGeral().id();
        conversas.adicionarParticipante(geral, a.id());
        conversas.adicionarParticipante(geral, b.id());
        for (int i = 1; i <= 5; i++) {
            mensagens.salvar(Mensagem.nova(geral, i % 2 == 0 ? b : a, "geral " + i, Instant.now()));
        }
        long privada = conversas.obterOuCriarPrivada(a.id(), b.id()).id();
        mensagens.salvar(Mensagem.nova(privada, a, "só privada", Instant.now()));
        assertEquals(List.of("geral 3", "geral 4", "geral 5"),
                mensagens.buscarUltimas(3).stream().map(Mensagem::conteudo).toList());
        assertEquals(5, mensagens.buscarUltimas(50).size(), "a conversa privada não entra no histórico geral");
        assertThrows(IllegalArgumentException.class, () -> mensagens.buscarUltimas(0));
        assertEquals(1, mensagens.buscarUltimas(privada, 10).size(), "a versão por conversa continua funcionando");
    }
}
