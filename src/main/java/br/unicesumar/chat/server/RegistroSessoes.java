package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lista sincronizada das conexões ativas. Recurso compartilhado entre todas as threads de
 * ClienteHandler e a thread que aceita conexões: toda leitura e escrita passa pelo monitor da lista.
 * Quem percorre a lista trabalha numa cópia (snapshot), para não segurar o lock enquanto envia.
 */
public class RegistroSessoes {

    private final List<Sessao> sessoes = Collections.synchronizedList(new ArrayList<>());

    public boolean adicionar(Sessao sessao) {
        return sessoes.add(sessao);
    }

    public boolean remover(Sessao sessao) {
        return sessoes.remove(sessao);
    }

    public int total() {
        return sessoes.size();
    }

    public List<Sessao> snapshot() {
        synchronized (sessoes) {
            return List.copyOf(sessoes);
        }
    }

    public List<Sessao> sessoesDoUsuario(long usuarioId) {
        List<Sessao> resultado = new ArrayList<>();
        for (Sessao s : snapshot()) {
            if (s.usuario().map(u -> u.id() == usuarioId).orElse(false)) {
                resultado.add(s);
            }
        }
        return resultado;
    }

    public Set<Long> usuariosOnline() {
        Set<Long> ids = new LinkedHashSet<>();
        for (Sessao s : snapshot()) {
            s.usuario().map(Usuario::id).ifPresent(ids::add);
        }
        return ids;
    }

    public boolean usuarioOnline(long usuarioId) {
        return !sessoesDoUsuario(usuarioId).isEmpty();
    }
}
