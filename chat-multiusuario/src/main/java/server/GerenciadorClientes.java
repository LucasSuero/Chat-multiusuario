package server;

import model.Protocolo;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda a lista de clientes conectados e faz o broadcast.
 *
 * <p>A lista é o recurso compartilhado do servidor: cada cliente roda em sua própria
 * thread e todas mexem nela (entrar, sair, enviar mensagem). Todo acesso passa pelo
 * bloco {@code synchronized (trava)}, o que evita race conditions e garante que dois
 * broadcasts simultâneos não se misturem: todos os clientes recebem as mensagens na
 * mesma ordem.
 */
public class GerenciadorClientes {

    private final Object trava = new Object();
    private final List<Destinatario> clientes = new ArrayList<>();

    /**
     * Tenta incluir um cliente. Recusa se já existe alguém com o mesmo nome (sem
     * diferenciar maiúsculas de minúsculas). Dentro da mesma região protegida, avisa
     * o cliente com "OK", então ele nunca recebe uma mensagem antes da confirmação.
     */
    public boolean registrar(Destinatario novo) {
        synchronized (trava) {
            for (Destinatario existente : clientes) {
                if (existente.getNome().equalsIgnoreCase(novo.getNome())) {
                    return false;
                }
            }
            if (!novo.enviar(Protocolo.OK)) {
                return false;
            }
            clientes.add(novo);
            return true;
        }
    }

    public boolean remover(Destinatario cliente) {
        synchronized (trava) {
            return clientes.remove(cliente);
        }
    }

    /**
     * Envia o texto para todos os clientes. Quem não conseguir receber (conexão
     * caída) é removido da lista, sem atrapalhar a entrega para os demais.
     *
     * @return quantos clientes receberam o texto
     */
    public int broadcast(String texto) {
        List<Destinatario> falhos = new ArrayList<>();
        int entregues = 0;
        synchronized (trava) {
            for (Destinatario cliente : clientes) {
                if (cliente.enviar(texto)) {
                    entregues++;
                } else {
                    falhos.add(cliente);
                }
            }
            clientes.removeAll(falhos);
        }
        falhos.forEach(Destinatario::fechar); // fora da região protegida
        return entregues;
    }

    public int quantidade() {
        synchronized (trava) {
            return clientes.size();
        }
    }

    /** Remove e desconecta todos (usado quando o servidor é parado). */
    public void desconectarTodos() {
        List<Destinatario> copia;
        synchronized (trava) {
            copia = new ArrayList<>(clientes);
            clientes.clear();
        }
        copia.forEach(Destinatario::fechar);
    }
}
