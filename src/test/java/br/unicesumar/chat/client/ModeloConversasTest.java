package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoConversa;
import br.unicesumar.chat.model.Usuario;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModeloConversasTest {

    private final Usuario eu = new Usuario(1, "ana", "avatar01");
    private final Usuario bruno = new Usuario(2, "bruno", "avatar02");
    private final Usuario carla = new Usuario(3, "carla", "avatar03");

    private ConversaVisao privada(long id, Usuario parceiro) {
        return new ConversaVisao(new Conversa(id, TipoConversa.PRIVADA, null, null, null), parceiro);
    }

    private ConversaVisao grupo(long id, String nome, String imagem) {
        return new ConversaVisao(new Conversa(id, TipoConversa.GRUPO, nome, imagem, null), null);
    }

    private Mensagem msg(long id, long conversa, Usuario de, String texto, String instante) {
        return new Mensagem(id, conversa, de, texto, Instant.parse(instante));
    }

    @Test
    void separaPrivadasDeGruposEUsaOTituloCerto() {
        ModeloConversas m = new ModeloConversas();
        m.atualizarTodas(List.of(privada(10, bruno), grupo(11, "Turma POO", "grupo03"), privada(12, carla)));
        assertEquals(List.of("bruno", "carla"), m.privadas().stream().map(ModeloConversas.Item::titulo).toList());
        assertEquals(List.of("Turma POO"), m.grupos().stream().map(ModeloConversas.Item::titulo).toList());
        assertEquals("avatar02", m.privadas().get(0).imagem(), "conversa privada mostra o avatar do parceiro");
        assertEquals("grupo03", m.grupos().get(0).imagem());
        assertEquals("grupo01", grupoSemImagem().grupos().get(0).imagem());
    }

    private ModeloConversas grupoSemImagem() {
        ModeloConversas m = new ModeloConversas();
        m.atualizar(grupo(5, "Sem imagem", null));
        return m;
    }

    @Test
    void mensagemRecebidaNaoLidaQuandoConversaFechadaEPreviaAtualiza() {
        ModeloConversas m = new ModeloConversas();
        m.atualizar(privada(10, bruno));
        m.mensagemRecebida(msg(1, 10, bruno, "oi\nana", "2026-11-10T10:00:00Z"), false, eu.id());
        ModeloConversas.Item i = m.buscar(10).orElseThrow();
        assertEquals(1, i.naoLidas());
        assertEquals("oi ana", i.previa());
        m.marcarLida(10);
        assertEquals(0, m.buscar(10).orElseThrow().naoLidas());
    }

    @Test
    void conversaAbertaOuMensagemPropriaNaoContamComoNaoLida() {
        ModeloConversas m = new ModeloConversas();
        m.atualizar(privada(10, bruno));
        m.mensagemRecebida(msg(1, 10, bruno, "aberta", "2026-11-10T10:00:00Z"), true, eu.id());
        m.mensagemRecebida(msg(2, 10, eu, "minha", "2026-11-10T10:01:00Z"), false, eu.id());
        assertEquals(0, m.totalNaoLidas());
        assertEquals("Você: minha", m.buscar(10).orElseThrow().previa());
    }

    @Test
    void previaDeGrupoMostraQuemFalouETextoLongoEhCortado() {
        ModeloConversas m = new ModeloConversas();
        m.atualizar(grupo(11, "Turma", null));
        m.mensagemRecebida(msg(1, 11, carla, "x".repeat(200), "2026-11-10T10:00:00Z"), false, eu.id());
        String previa = m.buscar(11).orElseThrow().previa();
        assertTrue(previa.startsWith("carla: "));
        assertTrue(previa.length() <= "carla: ".length() + 60);
        assertTrue(previa.endsWith("…"));
    }

    @Test
    void conversasMaisRecentesVemPrimeiroEAtualizacaoPreservaEstado() {
        ModeloConversas m = new ModeloConversas();
        m.atualizarTodas(List.of(privada(10, bruno), privada(12, carla)));
        m.mensagemRecebida(msg(1, 12, carla, "oi", "2026-11-10T10:00:00Z"), false, eu.id());
        assertEquals(List.of("carla", "bruno"), m.privadas().stream().map(ModeloConversas.Item::titulo).toList());
        m.atualizar(privada(12, carla));
        assertEquals(1, m.buscar(12).orElseThrow().naoLidas(), "recarregar a lista não zera não lidas");
        assertEquals(2, m.tamanho());
    }

    @Test
    void mensagemAntigaDoHistoricoNaoSobrescrevePreviaMaisNova() {
        ModeloConversas m = new ModeloConversas();
        m.atualizar(privada(10, bruno));
        m.mensagemRecebida(msg(2, 10, bruno, "nova", "2026-11-10T10:05:00Z"), true, eu.id());
        m.mensagemRecebida(msg(1, 10, bruno, "antiga", "2026-11-10T10:00:00Z"), true, eu.id());
        assertEquals("nova", m.buscar(10).orElseThrow().previa());
    }

    @Test
    void mensagemDeConversaDesconhecidaEIgnorada() {
        ModeloConversas m = new ModeloConversas();
        m.mensagemRecebida(msg(1, 99, bruno, "oi", "2026-11-10T10:00:00Z"), false, eu.id());
        assertEquals(0, m.tamanho());
    }

    @Test
    void salaGeralFicaSempreNoTopoDosGruposEEhReconhecida() {
        ModeloConversas m = new ModeloConversas();
        m.atualizarTodas(List.of(grupo(11, "Turma POO", "grupo03"), grupo(5, "Geral", "grupo01"), privada(10, bruno)));
        m.mensagemRecebida(msg(1, 11, carla, "recente", "2026-11-10T10:00:00Z"), false, eu.id());
        assertEquals(List.of("Geral", "Turma POO"), m.grupos().stream().map(ModeloConversas.Item::titulo).toList());
        assertTrue(m.grupos().get(0).ehGeral());
        assertFalse(m.grupos().get(1).ehGeral());
        assertFalse(m.privadas().get(0).ehGeral());
        assertEquals(5, m.geral().orElseThrow().id());
    }
}
