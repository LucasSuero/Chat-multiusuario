package br.unicesumar.chat.persistence;

public class UsuarioDuplicadoException extends RuntimeException {
    public UsuarioDuplicadoException(String username) {
        super("Nome de usuário já existe: " + username);
    }
}
