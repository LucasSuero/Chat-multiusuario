package server;

import model.Mensagem;
import persistence.RepositorioException;
import persistence.RepositorioMensagens;

import java.util.List;
import java.util.function.Consumer;

/**
 * Regras do chat que ligam os três recursos compartilhados: a lista de clientes
 * ({@link GerenciadorClientes}), o banco de dados e o histórico.
 *
 * <p>Todas as operações que mudam o que os clientes veem (enviar mensagem, avisar entrada
 * ou saída, entrar e receber o histórico) passam por uma única trava, {@code ordem}.
 * Isso garante duas coisas:
 * <ul>
 *   <li>a ordem em que as mensagens são gravadas no banco é a mesma em que são entregues;</li>
 *   <li>um cliente novo recebe o histórico e passa a receber as mensagens ao vivo sem
 *       perder nem repetir nenhuma, pois nada é enviado enquanto ele está entrando.</li>
 * </ul>
 * Se o banco falhar, o erro vai para o log e o chat continua funcionando.
 */
public class ServicoChat {

    /** Quantas mensagens antigas um cliente novo recebe. */
    public static final int TAMANHO_HISTORICO = 50;
    public static final String INICIO_HISTORICO = "— Histórico recente —";
    public static final String FIM_HISTORICO = "— Fim do histórico —";

    private final GerenciadorClientes gerenciador;
    private final RepositorioMensagens repositorio;
    private final Consumer<String> log;
    private final Object ordem = new Object();

    public ServicoChat(GerenciadorClientes gerenciador, RepositorioMensagens repositorio,
                       Consumer<String> log) {
        this.gerenciador = gerenciador;
        this.repositorio = repositorio;
        this.log = log;
    }

    /**
     * Registra o cliente (que recebe "OK") e em seguida envia o histórico recente.
     *
     * @return false se o nome já está em uso
     */
    public boolean entrar(Destinatario novo) {
        synchronized (ordem) {
            if (!gerenciador.registrar(novo)) {
                return false;
            }
            enviarHistorico(novo);
            return true;
        }
    }

    /**
     * Cria a mensagem (com o horário de agora), grava no banco e envia a todos.
     *
     * @throws IllegalArgumentException se a mensagem for inválida (nada é gravado nem enviado)
     */
    public void enviarMensagem(String remetente, String texto) {
        synchronized (ordem) {
            Mensagem mensagem = Mensagem.nova(remetente, texto);
            try {
                repositorio.salvar(mensagem);
            } catch (RepositorioException e) {
                log.accept("Mensagem de " + remetente + " não gravada no histórico: " + causa(e));
            }
            gerenciador.broadcast(mensagem.formatar());
        }
    }

    /** Envia um aviso do sistema a todos (não é gravado no histórico). */
    public void anunciar(String texto) {
        synchronized (ordem) {
            gerenciador.broadcast(Mensagem.doSistema(texto).formatar());
        }
    }

    public void sair(Destinatario cliente) {
        gerenciador.remover(cliente);
    }

    public int quantidade() {
        return gerenciador.quantidade();
    }

    private void enviarHistorico(Destinatario novo) {
        List<Mensagem> ultimas;
        try {
            ultimas = repositorio.buscarUltimas(TAMANHO_HISTORICO);
        } catch (RepositorioException e) {
            log.accept("Histórico indisponível para " + novo.getNome() + ": " + causa(e));
            return;
        }
        if (ultimas.isEmpty() || !novo.enviar(INICIO_HISTORICO)) {
            return;
        }
        for (Mensagem m : ultimas) {
            if (!novo.enviar(m.formatar())) {
                return;
            }
        }
        novo.enviar(FIM_HISTORICO);
    }

    private static String causa(RepositorioException e) {
        Throwable c = e.getCause() != null ? e.getCause() : e;
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }
}
