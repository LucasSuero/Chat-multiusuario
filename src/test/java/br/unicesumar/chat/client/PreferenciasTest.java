package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreferenciasTest {

    private Path novaPasta() throws IOException {
        return Files.createTempDirectory("prefs-teste");
    }

    @Test
    void valoresPadraoQuandoNaoHaArquivo() throws IOException {
        Preferencias p = new Preferencias(novaPasta().resolve("nao-existe").resolve("p.properties"));
        assertEquals(Tema.ESCURO, p.tema());
        assertEquals("localhost", p.host());
        assertEquals(5000, p.porta());
        assertEquals("", p.ultimoUsuario());
    }

    @Test
    void temaEServidorPersistemEntreExecucoes() throws IOException {
        Path arquivo = novaPasta().resolve("sub").resolve("p.properties");
        Preferencias p = new Preferencias(arquivo);
        p.definirTema(Tema.CLARO);
        p.definirServidor("192.168.0.10", 6001);
        p.definirUltimoUsuario(" ana ");
        Preferencias outra = new Preferencias(arquivo);
        assertEquals(Tema.CLARO, outra.tema());
        assertEquals("192.168.0.10", outra.host());
        assertEquals(6001, outra.porta());
        assertEquals("ana", outra.ultimoUsuario());
    }

    @Test
    void alternarTemaVariasVezesGravaOUltimo() throws IOException {
        Path arquivo = novaPasta().resolve("p.properties");
        Preferencias p = new Preferencias(arquivo);
        Tema t = p.tema();
        for (int i = 0; i < 3; i++) {
            t = t.alternar();
            p.definirTema(t);
        }
        assertEquals(t, new Preferencias(arquivo).tema());
    }

    @Test
    void arquivoCorrompidoOuValoresInvalidosCaemNosPadroes() throws IOException {
        Path arquivo = novaPasta().resolve("p.properties");
        Files.writeString(arquivo, "tema=roxo\nporta=abc\nhost=   \n");
        Preferencias p = new Preferencias(arquivo);
        assertEquals(Tema.ESCURO, p.tema());
        assertEquals(5000, p.porta());
        assertEquals("localhost", p.host());
        Files.writeString(arquivo, "porta=99999\n");
        assertEquals(5000, new Preferencias(arquivo).porta());
    }

    @Test
    void naoGravaSenhaNemQualquerChaveInesperada() throws IOException {
        Path arquivo = novaPasta().resolve("p.properties");
        Preferencias p = new Preferencias(arquivo);
        p.definirUltimoUsuario("ana");
        p.definirTema(Tema.CLARO);
        String conteudo = Files.readString(arquivo).toLowerCase();
        assertFalse(conteudo.contains("senha") && conteudo.contains("senha="), "preferências não devem ter senha");
    }
}
