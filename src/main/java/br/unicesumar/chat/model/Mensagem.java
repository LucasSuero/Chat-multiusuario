package br.unicesumar.chat.model;

import java.time.Instant;

/** Mensagem de uma conversa. Registra remetente (com avatar) e horário. id = 0 indica ainda não persistida. */
public record Mensagem(long id, long conversaId, Usuario remetente, String conteudo, Instant enviadaEm) {

    public static final int TAMANHO_MAXIMO = 2000;

    public Mensagem {
        if (remetente == null) {
            throw new IllegalArgumentException("remetente obrigatório");
        }
        if (enviadaEm == null) {
            throw new IllegalArgumentException("horário obrigatório");
        }
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("mensagem vazia");
        }
        conteudo = conteudo.strip();
        if (conteudo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("mensagem excede " + TAMANHO_MAXIMO + " caracteres");
        }
    }

    public static Mensagem nova(long conversaId, Usuario remetente, String conteudo, Instant agora) {
        return new Mensagem(0, conversaId, remetente, conteudo, agora);
    }

    public Mensagem comId(long novoId) {
        return new Mensagem(novoId, conversaId, remetente, conteudo, enviadaEm);
    }

    public boolean enviadaPor(long usuarioId) {
        return remetente.id() == usuarioId;
    }
}
