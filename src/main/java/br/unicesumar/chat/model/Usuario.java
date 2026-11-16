package br.unicesumar.chat.model;

/** Usuário do sistema. A senha nunca faz parte do modelo (fica só como hash na persistência). */
public record Usuario(long id, String username, String avatar) {
    public Usuario {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username obrigatório");
        }
        if (avatar == null || avatar.isBlank()) {
            avatar = "avatar01";
        }
    }
}
