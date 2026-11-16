package br.unicesumar.chat.service;

/** Erro de regra de negócio com código para o protocolo. */
public class ChatException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final Erro erro;

    public ChatException(Erro erro, String mensagem) {
        super(mensagem);
        this.erro = erro;
    }

    public Erro erro() {
        return erro;
    }
}
