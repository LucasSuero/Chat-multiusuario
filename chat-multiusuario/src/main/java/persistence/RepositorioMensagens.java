package persistence;

import model.Mensagem;

import java.util.List;

/**
 * Contrato de armazenamento das mensagens. {@link MensagemRepository} é a implementação
 * com JDBC; ter a interface permite testar as regras do chat sem banco de dados.
 */
public interface RepositorioMensagens {

    void salvar(Mensagem mensagem);

    /** Devolve até {@code quantidade} mensagens, da mais antiga para a mais recente. */
    List<Mensagem> buscarUltimas(int quantidade);
}
