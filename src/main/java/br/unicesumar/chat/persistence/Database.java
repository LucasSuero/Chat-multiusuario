package br.unicesumar.chat.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fábrica de conexões JDBC. Cada operação abre sua própria conexão (seguro entre threads). */
public final class Database {

    private static final String SCHEMA = "/sql/schema.sql";
    private final String url;

    public Database(String url) {
        this.url = Objects.requireNonNull(url);
    }

    public static Database emArquivo(Path arquivo) {
        return new Database("jdbc:sqlite:" + arquivo.toAbsolutePath());
    }

    public Connection abrir() {
        try {
            Connection c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
            }
            return c;
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao abrir conexão com o banco", e);
        }
    }

    /** Cria as tabelas caso ainda não existam (idempotente). */
    public void inicializar() {
        try (Connection c = abrir(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            for (String comando : lerComandos()) {
                st.execute(comando);
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao criar o esquema do banco", e);
        }
    }

    private List<String> lerComandos() {
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado: " + SCHEMA);
            }
            String texto = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StringBuilder sem = new StringBuilder();
            for (String linha : texto.split("\\R")) {
                if (!linha.strip().startsWith("--")) {
                    sem.append(linha).append('\n');
                }
            }
            List<String> comandos = new ArrayList<>();
            for (String parte : sem.toString().split(";")) {
                if (!parte.isBlank()) {
                    comandos.add(parte.strip());
                }
            }
            return comandos;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + SCHEMA, e);
        }
    }
}
