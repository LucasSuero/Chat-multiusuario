package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.TipoConversa;
import br.unicesumar.chat.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Único ponto de acesso (SQL) a conversa e conversa_participante. */
public class ConversaRepository {

    private final Database db;

    public ConversaRepository(Database db) {
        this.db = db;
    }

    /** Retorna a conversa privada existente entre os dois usuários ou cria uma nova. */
    public synchronized Conversa obterOuCriarPrivada(long usuarioA, long usuarioB) {
        if (usuarioA == usuarioB) {
            throw new IllegalArgumentException("Conversa privada exige dois usuários diferentes");
        }
        String busca = "SELECT c.id, c.tipo, c.nome, c.imagem, c.criador_id FROM conversa c "
                + "JOIN conversa_participante p1 ON p1.conversa_id = c.id AND p1.usuario_id = ? "
                + "JOIN conversa_participante p2 ON p2.conversa_id = c.id AND p2.usuario_id = ? "
                + "WHERE c.tipo = 'PRIVADA'";
        try (Connection c = db.abrir()) {
            try (PreparedStatement ps = c.prepareStatement(busca)) {
                ps.setLong(1, usuarioA);
                ps.setLong(2, usuarioB);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapear(rs);
                    }
                }
            }
            return inserir(c, TipoConversa.PRIVADA, null, null, usuarioA, List.of(usuarioA, usuarioB));
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao obter conversa privada", e);
        }
    }

    private static final String SELECT_GERAL = "SELECT id, tipo, nome, imagem, criador_id FROM conversa "
            + "WHERE tipo = 'GRUPO' AND criador_id IS NULL AND nome = 'Geral' ORDER BY id LIMIT 1";
    private static final String INSERT_GERAL = "INSERT INTO conversa (tipo, nome, imagem) "
            + "SELECT 'GRUPO', 'Geral', 'grupo01' WHERE NOT EXISTS "
            + "(SELECT 1 FROM conversa WHERE tipo = 'GRUPO' AND criador_id IS NULL AND nome = 'Geral')";

    /**
     * Sala Geral (broadcast para todos): devolve a existente ou cria. Identificada por criador nulo, então
     * nunca se confunde com um grupo de usuário chamado "Geral" nem com conversas de bancos antigos.
     */
    public synchronized Conversa obterOuCriarGeral() {
        try (Connection c = db.abrir()) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate(INSERT_GERAL);
            }
            try (PreparedStatement ps = c.prepareStatement(SELECT_GERAL); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapear(rs);
                }
            }
            throw new PersistenciaException("Sala Geral não encontrada", null);
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao obter a sala Geral", e);
        }
    }

    /** Cria um grupo; o criador sempre é incluído como participante. */
    public Conversa criarGrupo(String nome, String imagem, long criadorId, Collection<Long> participantes) {
        List<Long> todos = new ArrayList<>(participantes);
        if (!todos.contains(criadorId)) {
            todos.add(criadorId);
        }
        try (Connection c = db.abrir()) {
            return inserir(c, TipoConversa.GRUPO, nome, imagem, criadorId, todos);
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao criar grupo", e);
        }
    }

    public void adicionarParticipante(long conversaId, long usuarioId) {
        String sql = "INSERT OR IGNORE INTO conversa_participante (conversa_id, usuario_id) VALUES (?, ?)";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversaId);
            ps.setLong(2, usuarioId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao adicionar participante", e);
        }
    }

    public boolean ehParticipante(long conversaId, long usuarioId) {
        String sql = "SELECT 1 FROM conversa_participante WHERE conversa_id = ? AND usuario_id = ?";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversaId);
            ps.setLong(2, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao verificar participante", e);
        }
    }

    public List<Usuario> listarParticipantes(long conversaId) {
        String sql = "SELECT u.id, u.username, u.avatar FROM usuario u "
                + "JOIN conversa_participante p ON p.usuario_id = u.id "
                + "WHERE p.conversa_id = ? ORDER BY u.username";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, conversaId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Usuario> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(new Usuario(rs.getLong("id"), rs.getString("username"), rs.getString("avatar")));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao listar participantes", e);
        }
    }

    public List<Conversa> listarDoUsuario(long usuarioId) {
        String sql = "SELECT c.id, c.tipo, c.nome, c.imagem, c.criador_id FROM conversa c "
                + "JOIN conversa_participante p ON p.conversa_id = c.id "
                + "WHERE p.usuario_id = ? ORDER BY c.id";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Conversa> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao listar conversas", e);
        }
    }

    public Optional<Conversa> buscarPorId(long id) {
        String sql = "SELECT id, tipo, nome, imagem, criador_id FROM conversa WHERE id = ?";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao buscar conversa", e);
        }
    }

    private Conversa inserir(Connection c, TipoConversa tipo, String nome, String imagem,
                             long criadorId, Collection<Long> participantes) throws SQLException {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            long id;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO conversa (tipo, nome, imagem, criador_id) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, tipo.name());
                ps.setString(2, nome);
                ps.setString(3, imagem);
                ps.setLong(4, criadorId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO conversa_participante (conversa_id, usuario_id) VALUES (?, ?)")) {
                for (Long uid : participantes) {
                    ps.setLong(1, id);
                    ps.setLong(2, uid);
                    ps.executeUpdate();
                }
            }
            c.commit();
            return new Conversa(id, tipo, nome, imagem, criadorId);
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    private static Conversa mapear(ResultSet rs) throws SQLException {
        long criador = rs.getLong("criador_id");
        Long criadorId = rs.wasNull() ? null : criador;
        return new Conversa(rs.getLong("id"), TipoConversa.valueOf(rs.getString("tipo")),
                rs.getString("nome"), rs.getString("imagem"), criadorId);
    }
}
