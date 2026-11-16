package br.unicesumar.chat.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ValidadorTest {

    @Test
    void aceitaUsernameValidoRemovendoEspacos() {
        assertEquals("ana_01", Validador.username(" ana_01 "));
    }

    @Test
    void rejeitaUsernameComEspacoOuMuitoCurto() {
        assertThrows(ValidacaoException.class, () -> Validador.username("a b"));
        assertThrows(ValidacaoException.class, () -> Validador.username("ab"));
        assertThrows(ValidacaoException.class, () -> Validador.username(null));
    }

    @Test
    void rejeitaSenhaCurta() {
        assertThrows(ValidacaoException.class, () -> Validador.senha("123".toCharArray()));
        assertDoesNotThrow(() -> Validador.senha("123456".toCharArray()));
    }

    @Test
    void validaNomeDeGrupo() {
        assertEquals("Turma POO", Validador.nomeGrupo("  Turma POO "));
        assertThrows(ValidacaoException.class, () -> Validador.nomeGrupo("   "));
        assertThrows(ValidacaoException.class, () -> Validador.nomeGrupo("x".repeat(41)));
    }

    @Test
    void validaAvatarEImagemDeGrupo() {
        assertEquals("avatar01", Validador.avatar(null));
        assertEquals("avatar07", Validador.avatar(" avatar07 "));
        assertThrows(ValidacaoException.class, () -> Validador.avatar("../../etc/passwd"));
        assertNull(Validador.imagemGrupo("  "));
        assertEquals("grupo02", Validador.imagemGrupo("grupo02"));
        assertThrows(ValidacaoException.class, () -> Validador.imagemGrupo("<script>"));
    }
}
