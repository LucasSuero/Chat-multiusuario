package persistence;

import model.Mensagem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Único ponto do sistema que fala SQL. Cada operação abre e fecha a sua própria
 * conexão, então vários clientes podem usar o repositório ao mesmo tempo sem
 * dividir estado entre as threads.
 */
public class MensagemRepository {

    private static final String SQL_INSERIR =
            "INSERT INTO mensagens (remetente, conteudo, horario) VALUES (?, ?, ?)";

    private static final String SQL_ULTIMAS =
            "SELECT remetente, conteudo, horario FROM mensagens "
                    + "ORDER BY horario DESC, id DESC LIMIT ?";

    private final ConfiguracaoBanco configuracao;

    public MensagemRepository(ConfiguracaoBanco configuracao) {
        this.configuracao = configuracao;
    }

    public void salvar(Mensagem mensagem) {
        try (Connection conexao = abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR)) {
            comando.setString(1, mensagem.getRemetente());
            comando.setString(2, mensagem.getConteudo());
            comando.setTimestamp(3, Timestamp.valueOf(mensagem.getHorario()));
            comando.executeUpdate();
        } catch (SQLException e) {
            throw new RepositorioException("Não foi possível salvar a mensagem.", e);
        }
    }

    /** Devolve até {@code quantidade} mensagens, da mais antiga para a mais recente. */
    public List<Mensagem> buscarUltimas(int quantidade) {
        if (quantidade <= 0) {
            throw new IllegalArgumentException("A quantidade deve ser maior que zero.");
        }
        List<Mensagem> mensagens = new ArrayList<>();
        try (Connection conexao = abrirConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_ULTIMAS)) {
            comando.setInt(1, quantidade);
            try (ResultSet resultado = comando.executeQuery()) {
                while (resultado.next()) {
                    mensagens.add(new Mensagem(
                            resultado.getString("remetente"),
                            resultado.getString("conteudo"),
                            resultado.getTimestamp("horario").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new RepositorioException("Não foi possível ler o histórico.", e);
        }
        Collections.reverse(mensagens); // a consulta vem da mais nova para a mais antiga
        return mensagens;
    }

    private Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(
                configuracao.url(), configuracao.usuario(), configuracao.senha());
    }
}
