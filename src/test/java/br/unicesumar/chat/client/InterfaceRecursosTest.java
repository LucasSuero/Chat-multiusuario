package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Conferências estáticas da interface JavaFX que não exigem abrir janelas: as classes de estilo
 * usadas no código existem no CSS, os caminhos de recursos apontam para arquivos reais e todo botão
 * criado tem uma ação ligada.
 */
class InterfaceRecursosTest {

    private static final Path PASTA_UI = Path.of("src/main/java/br/unicesumar/chat/client/ui");

    private static List<Path> fontesUi() throws IOException {
        try (Stream<Path> s = Files.list(PASTA_UI)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String ler(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static Set<String> classesDoCss() throws IOException {
        String css = ler(Path.of("src/main/resources/css/app.css")).replaceAll("(?s)/\\*.*?\\*/", "");
        String seletores = css.replaceAll("\\{[^}]*\\}", "{}");
        Set<String> r = new TreeSet<>();
        Matcher m = Pattern.compile("\\.([a-zA-Z][\\w-]*)").matcher(seletores);
        while (m.find()) {
            r.add(m.group(1));
        }
        return r;
    }

    @Test
    void todaClasseDeEstiloUsadaNoCodigoTemRegraNoCss() throws IOException {
        Set<String> noCss = classesDoCss();
        Set<String> nativas = Set.of("icone"); // só marca o contêiner do ícone, sem estilo próprio
        Pattern chamada = Pattern.compile("getStyleClass\\(\\)\\.(?:add|addAll|remove|removeAll|setAll)\\(([^;]*)\\);");
        Pattern estado = Pattern.compile("definirEstado\\([^;]*?\"(estado-conexao[\\w-]*)\"");
        List<String> faltando = new ArrayList<>();
        int total = 0;
        for (Path fonte : fontesUi()) {
            String src = ler(fonte);
            Matcher m = chamada.matcher(src);
            while (m.find()) {
                Matcher lit = Pattern.compile("\"([^\"]+)\"").matcher(m.group(1));
                while (lit.find()) {
                    total++;
                    if (!noCss.contains(lit.group(1)) && !nativas.contains(lit.group(1))) {
                        faltando.add(fonte.getFileName() + ": " + lit.group(1));
                    }
                }
            }
            Matcher e = estado.matcher(src);
            while (e.find()) {
                total++;
                if (!noCss.contains(e.group(1))) {
                    faltando.add(fonte.getFileName() + ": " + e.group(1));
                }
            }
        }
        assertTrue(total > 30, "a varredura deveria encontrar dezenas de classes, achou " + total);
        assertTrue(faltando.isEmpty(), "classes sem regra no app.css: " + faltando);
    }

    @Test
    void caminhosDeRecursosCitadosNaInterfaceExistem() throws IOException {
        Pattern recurso = Pattern.compile("\"(/css/[\\w.-]+)\"");
        int achados = 0;
        for (Path fonte : fontesUi()) {
            Matcher m = recurso.matcher(ler(fonte));
            while (m.find()) {
                achados++;
                assertNotNull(InterfaceRecursosTest.class.getResource(m.group(1)), "recurso inexistente: " + m.group(1));
            }
        }
        assertTrue(achados >= 1, "ChatApp deveria citar o app.css");
        for (Tema t : Tema.values()) {
            assertNotNull(InterfaceRecursosTest.class.getResource(t.recursoCss()), "tema sem arquivo: " + t);
        }
    }

    @Test
    void todoBotaoCriadoTemUmaAcao() throws IOException {
        Pattern criacao = Pattern.compile("(?:Toggle)?Button\\s+(\\w+)\\s*=\\s*new\\s+(Toggle)?Button\\(");
        List<String> semAcao = new ArrayList<>();
        int total = 0;
        for (Path fonte : fontesUi()) {
            String src = ler(fonte);
            Matcher m = criacao.matcher(src);
            while (m.find()) {
                total++;
                String nome = m.group(1);
                boolean direta = src.contains(nome + ".setOnAction(");
                // botões de alternância agem pelo ToggleGroup (listener), que pode ser ligado dentro de um laço
                boolean porGrupo = m.group(2) != null && src.contains(".setToggleGroup(");
                if (!direta && !porGrupo) {
                    semAcao.add(fonte.getFileName() + ": " + nome);
                }
            }
        }
        assertTrue(total >= 15, "esperava encontrar os botões da interface, achou " + total);
        assertTrue(semAcao.isEmpty(), "botões sem ação: " + semAcao);
    }

    @Test
    void interfaceSoUsaOClienteChatParaFalarComOServidor() throws IOException {
        for (Path fonte : fontesUi()) {
            String src = ler(fonte);
            assertFalse(src.contains("java.net."), fonte.getFileName() + " não deve abrir sockets");
            assertFalse(src.contains("import br.unicesumar.chat.persistence"), fonte.getFileName() + " não deve acessar o banco");
            assertFalse(src.contains("import br.unicesumar.chat.server"), fonte.getFileName() + " não deve usar classes do servidor");
        }
    }
}
