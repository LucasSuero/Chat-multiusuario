package br.unicesumar.chat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class FormatadorMensagemTest {

    @Test
    void formataHorarioNoFusoInformado() {
        Mensagem m = Mensagem.nova(1, new Usuario(1, "ana", null), "olá", Instant.parse("2026-11-10T17:05:00Z"));
        assertEquals("[14:05] ana: olá", FormatadorMensagem.formatar(m, ZoneId.of("America/Sao_Paulo")));
        assertEquals("[17:05] ana: olá", FormatadorMensagem.formatar(m, ZoneId.of("UTC")));
    }
}
