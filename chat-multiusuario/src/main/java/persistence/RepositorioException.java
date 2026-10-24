package persistence;

/** Erro ao conversar com o banco de dados. */
@SuppressWarnings("serial")
public class RepositorioException extends RuntimeException {

    public RepositorioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
