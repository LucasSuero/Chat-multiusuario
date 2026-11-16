package br.unicesumar.chat.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void aceitaSenhaCorreta() {
        String hash = PasswordHasher.gerar("segredo1".toCharArray());
        assertTrue(PasswordHasher.verificar("segredo1".toCharArray(), hash));
    }

    @Test
    void rejeitaSenhaErrada() {
        String hash = PasswordHasher.gerar("segredo1".toCharArray());
        assertFalse(PasswordHasher.verificar("outra".toCharArray(), hash));
    }

    @Test
    void usaSaltDiferenteACadaGeracao() {
        char[] senha = "segredo1".toCharArray();
        assertNotEquals(PasswordHasher.gerar(senha), PasswordHasher.gerar(senha));
    }

    @Test
    void naoGuardaSenhaEmTextoPuro() {
        assertFalse(PasswordHasher.gerar("segredo1".toCharArray()).contains("segredo1"));
    }

    @Test
    void formatoInvalidoNaoVerifica() {
        assertFalse(PasswordHasher.verificar("x".toCharArray(), "lixo"));
        assertFalse(PasswordHasher.verificar("x".toCharArray(), null));
    }
}
