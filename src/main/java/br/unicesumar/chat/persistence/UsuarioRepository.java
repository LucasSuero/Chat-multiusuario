package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Único ponto de acesso (SQL) à tabela usuario. */
public class UsuarioRepository {

    /** Usuário + hash da senha, usado apenas na autenticação. */
    public record Credencial(Usuario usuario, String senhaHash) {
    }

    private final Database db;

    public UsuarioRepository(Database db) {
        this.db = db;
    }

    public Usuario criar(String username, String senhaHash, String avatar) {
        String sql = "INSERT INTO usuario (username, senha_hash, avatar) VALUES (?, ?, ?)";
        try (Connection c = db.abrir();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, senhaHash);
            ps.setString(3, avatar);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return new Usuario(rs.getLong(1), username, avatar);
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                throw new UsuarioDuplicadoException(username);
            }
            throw new PersistenciaException("Falha ao criar usuário", e);
        }
    }

    public Optional<Credencial> buscarCredencial(String username) {
        String sql = "SELECT id, username, avatar, senha_hash FROM usuario WHERE username = ?";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Usuario u = new Usuario(rs.getLong("id"), rs.getString("username"), rs.getString("avatar"));
                return Optional.of(new Credencial(u, rs.getString("senha_hash")));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao buscar usuário", e);
        }
    }

    public Optional<Usuario> buscarPorId(long id) {
        String sql = "SELECT id, username, avatar FROM usuario WHERE id = ?";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao buscar usuário", e);
        }
    }

    /** Busca usuários pelo trecho do nome, excluindo o próprio solicitante. */
    public List<Usuario> pesquisar(String termo, long excluirId) {
        String escapado = termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String sql = "SELECT id, username, avatar FROM usuario "
                + "WHERE username LIKE ? ESCAPE '\\' AND id <> ? ORDER BY username LIMIT 20";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "%" + escapado + "%");
            ps.setLong(2, excluirId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Usuario> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao pesquisar usuários", e);
        }
    }

    public void atualizarAvatar(long id, String avatar) {
        String sql = "UPDATE usuario SET avatar = ? WHERE id = ?";
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, avatar);
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao atualizar avatar", e);
        }
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        return new Usuario(rs.getLong("id"), rs.getString("username"), rs.getString("avatar"));
    }
}
