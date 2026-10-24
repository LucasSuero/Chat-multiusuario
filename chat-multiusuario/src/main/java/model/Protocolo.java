package model;

/**
 * Combinado entre cliente e servidor. A conversa é feita em linhas de texto (UTF-8):
 * <ol>
 *   <li>O cliente conecta e envia uma linha com o seu nome.</li>
 *   <li>O servidor responde "OK" ou "ERRO:motivo" (e fecha a conexão neste caso).</li>
 *   <li>Depois disso, cada linha enviada pelo cliente é uma mensagem do chat, e cada
 *       linha recebida do servidor já vem formatada, pronta para exibir.</li>
 * </ol>
 */
public final class Protocolo {

    public static final int PORTA_PADRAO = 5000;
    public static final String OK = "OK";
    public static final String PREFIXO_ERRO = "ERRO:";

    private Protocolo() {
    }
}
