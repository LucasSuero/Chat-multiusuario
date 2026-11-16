package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Único ponto do sistema que grava e lê mensagens via JDBC. */
public class MensagemRepository {

    private final Database db;

    public MensagemRepository(Database db) {
        this.db = db;
    }

    /** Persiste a mensagem e devolve uma cópia com o id gerado. */
    public Mensagem salvar(Mensagem m) {
        String sql = "INSERT INTO mensagem (conversa_id, remetente_id, conteudo, enviada_em) VALUES (?, ?, ?, ?)";
        try (Connection c = db.abrir();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, m.conversaId());
            ps.setLong(2, m.remetente().id());
            ps.setString(3, m.conteudo());
            ps.setString(4, m.enviadaEm().toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return m.comId(rs.getLong(1));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao salvar mensagem", e);
        }
    }

    private static final String SELECT_BASE = "SELECT m.id, m.conversa_id, m.conteudo, m.enviada_em, "
            + "u.id AS uid, u.username, u.avatar "
            + "FROM mensagem m JOIN usuario u ON u.id = m.remetente_id ";

    /** As últimas n mensagens da conversa, em ordem cronológica (mais antiga primeiro). */
    public List<Mensagem> buscarUltimas(long conversaId, int n) {
        exigirPositivo(n);
        return consultar(SELECT_BASE + "WHERE m.conversa_id = ? ORDER BY m.id DESC LIMIT ?", conversaId, n);
    }

    /**
     * buscarUltimas(n) como pede a atividade: as últimas n mensagens do histórico geral (sala Geral, da qual
     * todos participam), em ordem cronológica.
     */
    public List<Mensagem> buscarUltimas(int n) {
        exigirPositivo(n);
        return consultar(SELECT_BASE + "WHERE m.conversa_id = (SELECT id FROM conversa WHERE tipo = 'GRUPO' "
                + "AND criador_id IS NULL AND nome = 'Geral' ORDER BY id LIMIT 1) ORDER BY m.id DESC LIMIT ?", n);
    }

    private static void exigirPositivo(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n deve ser positivo");
        }
    }

    private List<Mensagem> consultar(String sql, Object... parametros) {
        try (Connection c = db.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                if (parametros[i] instanceof Long l) {
                    ps.setLong(i + 1, l);
                } else {
                    ps.setInt(i + 1, (Integer) parametros[i]);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Mensagem> lista = new ArrayList<>();
                while (rs.next()) {
                    Usuario remetente = new Usuario(rs.getLong("uid"), rs.getString("username"), rs.getString("avatar"));
                    lista.add(new Mensagem(rs.getLong("id"), rs.getLong("conversa_id"), remetente,
                            rs.getString("conteudo"), Instant.parse(rs.getString("enviada_em"))));
                }
                Collections.reverse(lista);
                return lista;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Falha ao buscar histórico", e);
        }
    }
}
