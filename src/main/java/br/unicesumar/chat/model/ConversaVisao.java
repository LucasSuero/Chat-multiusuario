package br.unicesumar.chat.model;

/** Conversa como um usuário a enxerga: em conversas privadas, inclui o outro participante (parceiro). */
public record ConversaVisao(Conversa conversa, Usuario parceiro) {
}
