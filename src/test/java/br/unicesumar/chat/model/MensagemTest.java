package br.unicesumar.chat.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MensagemTest {

    private final Usuario ana = new Usuario(1, "ana", "avatar02");

    @Test
    void removeEspacosDoConteudo() {
        Mensagem m = Mensagem.nova(1, ana, "  olá  ", Instant.now());
        assertEquals("olá", m.conteudo());
        assertEquals(0, m.id());
    }

    @Test
    void rejeitaMensagemVazia() {
        assertThrows(IllegalArgumentException.class, () -> Mensagem.nova(1, ana, "   ", Instant.now()));
    }

    @Test
    void rejeitaMensagemLongaDemais() {
        String longa = "x".repeat(Mensagem.TAMANHO_MAXIMO + 1);
        assertThrows(IllegalArgumentException.class, () -> Mensagem.nova(1, ana, longa, Instant.now()));
    }

    @Test
    void identificaRemetente() {
        Mensagem m = Mensagem.nova(1, ana, "oi", Instant.now());
        assertTrue(m.enviadaPor(1));
        assertFalse(m.enviadaPor(2));
    }

    @Test
    void comIdPreservaOsDemaisCampos() {
        Mensagem m = Mensagem.nova(7, ana, "oi", Instant.EPOCH).comId(42);
        assertEquals(42, m.id());
        assertEquals(7, m.conversaId());
        assertEquals("oi", m.conteudo());
    }
}
