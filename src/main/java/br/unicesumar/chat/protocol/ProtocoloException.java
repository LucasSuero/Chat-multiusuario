package br.unicesumar.chat.protocol;

public class ProtocoloException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocoloException(String mensagem) {
        super(mensagem);
    }
}
