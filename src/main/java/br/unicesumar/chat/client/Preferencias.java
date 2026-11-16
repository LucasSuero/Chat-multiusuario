package br.unicesumar.chat.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Preferências do cliente (tema, servidor, último usuário) em um arquivo .properties.
 * Nunca guarda senha. Falhas de leitura/gravação não derrubam o aplicativo.
 */
public final class Preferencias {

    private static final Logger LOG = Logger.getLogger(Preferencias.class.getName());
    public static final String HOST_PADRAO = "localhost";
    public static final int PORTA_PADRAO = 5000;

    private final Path arquivo;
    private final Properties props = new Properties();

    public Preferencias(Path arquivo) {
        this.arquivo = arquivo;
        if (Files.isRegularFile(arquivo)) {
            try (InputStream in = Files.newInputStream(arquivo)) {
                props.load(in);
            } catch (IOException | IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Preferências ilegíveis, usando padrões", e);
                props.clear();
            }
        }
    }

    public static Preferencias padrao() {
        return new Preferencias(Path.of(System.getProperty("user.home"), ".chat-multiusuario", "preferencias.properties"));
    }

    public Tema tema() {
        return Tema.deNome(props.getProperty("tema"), Tema.ESCURO);
    }

    public void definirTema(Tema tema) {
        props.setProperty("tema", tema.name());
        salvar();
    }

    public String host() {
        String h = props.getProperty("host", HOST_PADRAO).strip();
        return h.isEmpty() ? HOST_PADRAO : h;
    }

    public int porta() {
        try {
            int p = Integer.parseInt(props.getProperty("porta", String.valueOf(PORTA_PADRAO)).strip());
            return p >= 1 && p <= 65535 ? p : PORTA_PADRAO;
        } catch (NumberFormatException e) {
            return PORTA_PADRAO;
        }
    }

    public void definirServidor(String host, int porta) {
        props.setProperty("host", host.strip());
        props.setProperty("porta", String.valueOf(porta));
        salvar();
    }

    public String ultimoUsuario() {
        return props.getProperty("ultimoUsuario", "");
    }

    public void definirUltimoUsuario(String usuario) {
        props.setProperty("ultimoUsuario", usuario == null ? "" : usuario.strip());
        salvar();
    }

    /** Grava de forma atômica (arquivo temporário + troca). Retorna false se não conseguiu. */
    public boolean salvar() {
        try {
            Path pasta = arquivo.toAbsolutePath().getParent();
            Files.createDirectories(pasta);
            Path temp = Files.createTempFile(pasta, "prefs", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "Chat Multiusuario - preferencias do cliente");
            }
            Files.move(temp, arquivo, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Não foi possível salvar as preferências", e);
            return false;
        }
    }
}
