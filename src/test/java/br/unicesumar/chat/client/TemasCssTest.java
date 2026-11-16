package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Garante, sem abrir interface, que os dois temas definem exatamente as mesmas variáveis de cor
 * e que todas as variáveis usadas em app.css existem nos dois temas (nenhuma cor "some" ao alternar).
 */
class TemasCssTest {

    private static String ler(String recurso) throws IOException {
        try (InputStream in = TemasCssTest.class.getResourceAsStream(recurso)) {
            assertNotNull(in, "recurso ausente: " + recurso);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String semComentarios(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static Set<String> definidas(String css) {
        Set<String> r = new TreeSet<>();
        Matcher m = Pattern.compile("(?m)^\\s*(-[a-z][a-z0-9-]*)\\s*:").matcher(semComentarios(css));
        while (m.find()) {
            if (!m.group(1).startsWith("-fx-")) {
                r.add(m.group(1));
            }
        }
        return r;
    }

    private static Set<String> usadas(String css) {
        Set<String> r = new TreeSet<>();
        Matcher valores = Pattern.compile(":([^;{}]*);").matcher(semComentarios(css));
        while (valores.find()) {
            Matcher v = Pattern.compile("(?<![\\w-])(-[a-z][a-z0-9-]*)").matcher(valores.group(1));
            while (v.find()) {
                if (!v.group(1).startsWith("-fx-")) {
                    r.add(v.group(1));
                }
            }
        }
        return r;
    }

    @Test
    void osDoisTemasDefinemAsMesmasVariaveis() throws IOException {
        Set<String> claro = definidas(ler(Tema.CLARO.recursoCss()));
        Set<String> escuro = definidas(ler(Tema.ESCURO.recursoCss()));
        assertFalse(claro.isEmpty());
        assertEquals(claro, escuro);
    }

    @Test
    void todaVariavelUsadaNoCssBaseExisteNosDoisTemas() throws IOException {
        Set<String> usadas = usadas(ler("/css/app.css"));
        assertFalse(usadas.isEmpty());
        for (Tema t : Tema.values()) {
            Set<String> faltando = new TreeSet<>(usadas);
            faltando.removeAll(definidas(ler(t.recursoCss())));
            assertTrue(faltando.isEmpty(), "variáveis sem definição no tema " + t + ": " + faltando);
        }
    }

    @Test
    void temaAlternaEntreClaroEEscuro() {
        assertEquals(Tema.ESCURO, Tema.CLARO.alternar());
        assertEquals(Tema.CLARO, Tema.ESCURO.alternar());
        assertEquals(Tema.CLARO, Tema.deNome(" claro ", Tema.ESCURO));
        assertEquals(Tema.ESCURO, Tema.deNome("inexistente", Tema.ESCURO));
        assertEquals(Tema.ESCURO, Tema.deNome(null, Tema.ESCURO));
    }
}
