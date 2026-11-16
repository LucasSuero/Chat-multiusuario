package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;
import java.util.Optional;

/**
 * Uma conexão de cliente, vista pela lógica do servidor. Abstrai o socket: os testes usam uma
 * implementação em memória, e ClienteHandler é a implementação real.
 */
public interface Sessao {

    /** Usuário autenticado nesta conexão (vazio antes do login). */
    Optional<Usuario> usuario();

    void definirUsuario(Usuario usuario);

    /** Enfileira uma linha para o cliente. Nunca bloqueia quem chama. */
    void enviar(String linha);

    /** Encerra a sessão depois de entregar o que já estava na fila. */
    void fechar();
}
