package br.unicesumar.chat.model;

/** Conversa particular (2 participantes) ou grupo (nome e imagem opcional). */
public record Conversa(long id, TipoConversa tipo, String nome, String imagem, Long criadorId) {

    /** Nome reservado da sala geral (broadcast para todos). Usuários não podem criar grupos com esse nome. */
    public static final String NOME_GERAL = "Geral";
}
