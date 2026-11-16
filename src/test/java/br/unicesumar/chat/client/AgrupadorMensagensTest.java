package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgrupadorMensagensTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private final Usuario ana = new Usuario(1, "ana", "avatar01");
    private final Usuario bruno = new Usuario(2, "bruno", "avatar02");
    private final LocalDate hoje = LocalDate.of(2026, 11, 10);

    private Mensagem m(long id, Usuario de, String instante) {
        return new Mensagem(id, 1, de, "texto " + id, Instant.parse(instante));
    }

    @Test
    void primeiraMensagemTemSeparadorDeDiaEInicioDeBloco() {
        AgrupadorMensagens.Linha l = AgrupadorMensagens.calcular(null, m(1, ana, "2026-11-10T09:00:00Z"), UTC, hoje);
        assertEquals("Hoje", l.separadorDia());
        assertTrue(l.inicioBloco());
    }

    @Test
    void mesmoRemetenteEmPoucosMinutosContinuaNoBloco() {
        List<AgrupadorMensagens.Linha> r = AgrupadorMensagens.calcularTodas(List.of(
                m(1, ana, "2026-11-10T09:00:00Z"),
                m(2, ana, "2026-11-10T09:02:00Z"),
                m(3, bruno, "2026-11-10T09:03:00Z"),
                m(4, bruno, "2026-11-10T09:30:00Z")), UTC, hoje);
        assertTrue(r.get(0).inicioBloco());
        assertFalse(r.get(1).inicioBloco());
        assertTrue(r.get(2).inicioBloco(), "trocou o remetente");
        assertTrue(r.get(3).inicioBloco(), "intervalo longo");
        assertNull(r.get(1).separadorDia());
    }

    @Test
    void mudancaDeDiaGeraSeparadorComRotulosHojeOntemEData() {
        List<AgrupadorMensagens.Linha> r = AgrupadorMensagens.calcularTodas(List.of(
                m(1, ana, "2026-11-01T23:59:00Z"),
                m(2, ana, "2026-11-09T10:00:00Z"),
                m(3, ana, "2026-11-10T00:01:00Z")), UTC, hoje);
        assertEquals("01/11/2026", r.get(0).separadorDia());
        assertEquals("Ontem", r.get(1).separadorDia());
        assertEquals("Hoje", r.get(2).separadorDia());
        assertTrue(r.get(2).inicioBloco());
    }

    @Test
    void fusoHorarioDefineOLimiteDoDia() {
        Mensagem tarde = m(1, ana, "2026-11-10T02:30:00Z");
        ZoneId saoPaulo = ZoneId.of("America/Sao_Paulo");
        assertEquals("Ontem", AgrupadorMensagens.calcular(null, tarde, saoPaulo, hoje).separadorDia());
        assertEquals("Hoje", AgrupadorMensagens.calcular(null, tarde, UTC, hoje).separadorDia());
    }
}
