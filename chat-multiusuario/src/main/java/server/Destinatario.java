package server;

/**
 * Quem pode receber mensagens do chat. O {@link GerenciadorClientes} conhece apenas
 * esta interface, e não sockets, o que permite testar o broadcast sem rede.
 */
public interface Destinatario {

    String getNome();

    /** Envia uma linha de texto. Devolve false se não foi possível entregar. */
    boolean enviar(String texto);

    /** Encerra a conexão com este destinatário. Pode ser chamado mais de uma vez. */
    void fechar();
}
