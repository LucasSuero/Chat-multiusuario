package br.unicesumar.chat.testsupport;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.server.Sessao;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** Sessão em memória: guarda as linhas que o servidor "enviou" ao cliente. */
public class SessaoFalsa implements Sessao {

    private final List<String> recebidas = new CopyOnWriteArrayList<>();
    private volatile Usuario usuario;
    private volatile boolean fechada;

    public SessaoFalsa() {
    }

    public SessaoFalsa(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public Optional<Usuario> usuario() {
        return Optional.ofNullable(usuario);
    }

    @Override
    public void definirUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    @Override
    public void enviar(String linha) {
        recebidas.add(linha);
    }

    @Override
    public void fechar() {
        fechada = true;
    }

    public boolean fechada() {
        return fechada;
    }

    public List<String> recebidas() {
        return List.copyOf(recebidas);
    }

    /** Linhas recebidas cujo primeiro campo é o evento informado (ex.: "MSG"). */
    public List<String> recebidas(String evento) {
        return recebidas.stream().filter(l -> l.equals(evento) || l.startsWith(evento + "\t")).toList();
    }

    /** Conteúdos (último campo) das mensagens MSG recebidas, em ordem. */
    public List<String> conteudos() {
        return recebidas("MSG").stream().map(l -> {
            List<String> campos = Protocolo.dividir(l);
            return campos.get(campos.size() - 1);
        }).toList();
    }
}
