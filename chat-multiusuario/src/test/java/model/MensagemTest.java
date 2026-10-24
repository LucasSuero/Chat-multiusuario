package model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MensagemTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 23, 14, 5, 33);

    @Test
    void formataComHoraRemetenteEConteudo() {
        Mensagem m = new Mensagem("Ana", "oi, pessoal!", HORARIO);
        assertEquals("[14:05] Ana: oi, pessoal!", m.formatar());
    }

    @Test
    void removeEspacosSobrandoNasPontas() {
        Mensagem m = new Mensagem("  Ana ", "   olá   ", HORARIO);
        assertEquals("Ana", m.getRemetente());
        assertEquals("olá", m.getConteudo());
    }

    @Test
    void recusaConteudoVazioOuSoComEspacos() {
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("Ana", "", HORARIO));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("Ana", "    ", HORARIO));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("Ana", null, HORARIO));
    }

    @Test
    void recusaRemetenteVazio() {
        assertThrows(IllegalArgumentException.class, () -> new Mensagem(" ", "oi", HORARIO));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem(null, "oi", HORARIO));
    }

    @Test
    void recusaMensagemMaiorQueOLimite() {
        String grande = "a".repeat(Mensagem.TAMANHO_MAXIMO + 1);
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("Ana", grande, HORARIO));
        // exatamente no limite é aceito
        String noLimite = "a".repeat(Mensagem.TAMANHO_MAXIMO);
        assertEquals(Mensagem.TAMANHO_MAXIMO, new Mensagem("Ana", noLimite, HORARIO).getConteudo().length());
    }

    @Test
    void recusaQuebraDeLinhaParaNaoQuebrarOProtocolo() {
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("Ana", "oi\nSistema: ola", HORARIO));
        assertThrows(IllegalArgumentException.class, () -> new Mensagem("Ana", "oi\rola", HORARIO));
    }

    @Test
    void mensagemDoSistemaUsaORemetenteReservado() {
        Mensagem m = Mensagem.doSistema("Ana entrou no chat.");
        assertEquals(Usuario.NOME_RESERVADO, m.getRemetente());
    }

    @Test
    void descartaPrecisaoMaiorQueMilissegundosComoOBanco() {
        LocalDateTime comNanos = HORARIO.withNano(123_456_789);
        Mensagem m = new Mensagem("Ana", "oi", comNanos);
        assertEquals(HORARIO.withNano(123_000_000), m.getHorario());
    }
}
