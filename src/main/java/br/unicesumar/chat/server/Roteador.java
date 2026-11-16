package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Eventos;
import br.unicesumar.chat.service.ServicoChat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Encaminha mensagens: persiste (via ServicoChat) e entrega somente às sessões dos participantes
 * da conversa. Não conhece sockets, só {@link Sessao}, por isso é testável sem rede.
 */
public class Roteador {

    private final ServicoChat servico;
    private final RegistroSessoes registro;
    // Um lock por conversa: salvar + entregar acontecem juntos, então todos os participantes
    // veem as mensagens da conversa na mesma ordem em que foram gravadas.
    private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();

    public Roteador(ServicoChat servico, RegistroSessoes registro) {
        this.servico = servico;
        this.registro = registro;
    }

    /** Salva a mensagem e a entrega (inclusive ao remetente, que a usa como confirmação). */
    public Mensagem enviar(Usuario remetente, long conversaId, String texto) {
        Object lock = locks.computeIfAbsent(conversaId, id -> new Object());
        synchronized (lock) {
            Mensagem salva = servico.enviarMensagem(remetente, conversaId, texto);
            entregar(salva);
            return salva;
        }
    }

    /** Entrega a mensagem às sessões dos participantes. Retorna quantas sessões receberam. */
    public int entregar(Mensagem mensagem) {
        Set<Long> participantes = servico.idsParticipantes(mensagem.conversaId());
        String linha = Eventos.msg(mensagem);
        int entregues = 0;
        for (Sessao s : registro.snapshot()) {
            boolean participa = s.usuario().map(u -> participantes.contains(u.id())).orElse(false);
            if (participa) {
                s.enviar(linha);
                entregues++;
            }
        }
        return entregues;
    }

    /**
     * Propaga o novo perfil para as OUTRAS sessões do mesmo usuário (a sessão de origem é tratada por
     * quem chamou), para que as mensagens seguintes enviadas por qualquer uma delas tragam o avatar novo.
     */
    public void propagarPerfil(Usuario novo, Sessao origem) {
        String evento = Eventos.perfilAtualizado(novo);
        for (Sessao s : registro.sessoesDoUsuario(novo.id())) {
            if (s != origem) {
                s.definirUsuario(novo);
                s.enviar(evento);
            }
        }
    }

    /** Envia uma linha a todas as sessões de um usuário (ex.: aviso de conversa nova). */
    public void enviarAoUsuario(long usuarioId, String linha) {
        for (Sessao s : registro.sessoesDoUsuario(usuarioId)) {
            s.enviar(linha);
        }
    }
}
