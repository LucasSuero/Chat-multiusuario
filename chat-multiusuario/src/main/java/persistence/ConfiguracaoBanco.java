package persistence;

/**
 * Dados de conexão com o MySQL. Os valores padrão servem para um MySQL local;
 * para mudar sem alterar o código, defina as variáveis de ambiente
 * CHAT_DB_URL, CHAT_DB_USUARIO e CHAT_DB_SENHA.
 */
public record ConfiguracaoBanco(String url, String usuario, String senha) {

    private static final String URL_PADRAO =
            "jdbc:mysql://localhost:3306/chat_db?useSSL=false&allowPublicKeyRetrieval=true";

    public static ConfiguracaoBanco padrao() {
        return new ConfiguracaoBanco(
                valor("CHAT_DB_URL", URL_PADRAO),
                valor("CHAT_DB_USUARIO", "root"),
                valor("CHAT_DB_SENHA", ""));
    }

    private static String valor(String variavel, String padrao) {
        String valor = System.getenv(variavel);
        return (valor == null || valor.isBlank()) ? padrao : valor;
    }
}
