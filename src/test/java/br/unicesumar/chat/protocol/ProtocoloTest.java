package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtocoloTest {

    @Test
    void codificarEDividirSaoInversasMesmoComCaracteresEspeciais() {
        String texto = "linha1\nlinha2\ttab \\ barra\r fim";
        String linha = Protocolo.codificar("MSG", 7, texto);
        assertFalse(linha.contains("\n"), "a linha não pode conter quebra de linha real");
        assertEquals(List.of("MSG", "7", texto), Protocolo.dividir(linha));
    }

    @Test
    void interpretaComandoComArgumentos() {
        Comando c = Protocolo.interpretar(Protocolo.codificar("msg", 12, "olá\tmundo"));
        assertEquals(Comando.Tipo.MSG, c.tipo());
        assertEquals(12, c.argLong(0));
        assertEquals("olá\tmundo", c.arg(1));
    }

    @Test
    void rejeitaComandoDesconhecidoEQuantidadeErradaDeArgumentos() {
        assertThrows(ProtocoloException.class, () -> Protocolo.interpretar("VOAR"));
        assertThrows(ProtocoloException.class, () -> Protocolo.interpretar("LOGIN\tsó-usuario"));
        assertThrows(ProtocoloException.class, () -> Protocolo.interpretar(""));
    }

    @Test
    void argumentosNumericosInvalidosViramErroDeProtocolo() {
        Comando c = Protocolo.interpretar("ENTRAR\tabc");
        assertThrows(ProtocoloException.class, () -> c.argLong(0));
        Comando g = Protocolo.interpretar("CRIAR_GRUPO\tTurma\t\t1, 2,3");
        assertEquals(List.of(1L, 2L, 3L), g.argListaLong(2));
    }

    @Test
    void campoVazioENuloSaoPreservadosComoVazio() {
        String linha = Protocolo.codificar("CONVERSA", 1, "GRUPO", null, "");
        assertEquals(List.of("CONVERSA", "1", "GRUPO", "", ""), Protocolo.dividir(linha));
    }
}
