package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoConversa;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Estado da lista de conversas (sem JavaFX): título, prévia da última mensagem, não lidas
 * e separação entre conversas particulares e grupos.
 */
public final class ModeloConversas {

    private static final int PREVIA_MAXIMA = 60;

    /** Linha da lista. ultimaAtividade é null enquanto nenhuma mensagem foi vista nesta sessão. */
    public record Item(ConversaVisao visao, String previa, Instant ultimaAtividade, int naoLidas) {

        public long id() {
            return visao.conversa().id();
        }

        public boolean ehGrupo() {
            return visao.conversa().tipo() == TipoConversa.GRUPO;
        }

        /** Sala Geral: grupo de nome reservado, do qual todo usuário participa (broadcast). */
        public boolean ehGeral() {
            return ehGrupo() && Conversa.NOME_GERAL.equals(visao.conversa().nome());
        }

        public String titulo() {
            return ModeloConversas.titulo(visao);
        }

        /** Id da imagem: avatar do parceiro (privada) ou imagem do grupo. */
        public String imagem() {
            if (ehGrupo()) {
                String img = visao.conversa().imagem();
                return img == null || img.isBlank() ? Avatares.PADRAO_GRUPO : img;
            }
            return visao.parceiro() == null ? Avatares.PADRAO_USUARIO : visao.parceiro().avatar();
        }
    }

    private final Map<Long, Item> itens = new LinkedHashMap<>();

    public static String titulo(ConversaVisao v) {
        if (v.conversa().tipo() == TipoConversa.PRIVADA && v.parceiro() != null) {
            return v.parceiro().username();
        }
        String nome = v.conversa().nome();
        return nome == null || nome.isBlank() ? "Conversa " + v.conversa().id() : nome;
    }

    /** Insere ou atualiza uma conversa, preservando prévia e não lidas. */
    public void atualizar(ConversaVisao v) {
        Item atual = itens.get(v.conversa().id());
        itens.put(v.conversa().id(), atual == null
                ? new Item(v, "", null, 0)
                : new Item(v, atual.previa(), atual.ultimaAtividade(), atual.naoLidas()));
    }

    public void atualizarTodas(List<ConversaVisao> conversas) {
        for (ConversaVisao v : conversas) {
            atualizar(v);
        }
    }

    /** Registra uma mensagem: atualiza prévia/atividade e conta como não lida se a conversa não está aberta. */
    public void mensagemRecebida(Mensagem m, boolean conversaAberta, long meuId) {
        Item atual = itens.get(m.conversaId());
        if (atual == null) {
            return;
        }
        boolean minha = m.enviadaPor(meuId);
        String texto = m.conteudo().replace('\n', ' ').replace('\t', ' ');
        if (texto.length() > PREVIA_MAXIMA) {
            texto = texto.substring(0, PREVIA_MAXIMA - 1) + "…";
        }
        String previa = minha ? "Você: " + texto
                : atual.ehGrupo() ? m.remetente().username() + ": " + texto : texto;
        Instant ultima = atual.ultimaAtividade() == null || m.enviadaEm().isAfter(atual.ultimaAtividade())
                ? m.enviadaEm() : atual.ultimaAtividade();
        boolean atualizaPrevia = atual.ultimaAtividade() == null || !m.enviadaEm().isBefore(atual.ultimaAtividade());
        int naoLidas = atual.naoLidas() + (!conversaAberta && !minha ? 1 : 0);
        itens.put(m.conversaId(), new Item(atual.visao(), atualizaPrevia ? previa : atual.previa(), ultima, naoLidas));
    }

    public void marcarLida(long conversaId) {
        Item atual = itens.get(conversaId);
        if (atual != null && atual.naoLidas() > 0) {
            itens.put(conversaId, new Item(atual.visao(), atual.previa(), atual.ultimaAtividade(), 0));
        }
    }

    public Optional<Item> buscar(long conversaId) {
        return Optional.ofNullable(itens.get(conversaId));
    }

    public Optional<Item> geral() {
        return itens.values().stream().filter(Item::ehGeral).findFirst();
    }

    public List<Item> privadas() {
        return ordenadas(false);
    }

    public List<Item> grupos() {
        return ordenadas(true);
    }

    public int totalNaoLidas() {
        return itens.values().stream().mapToInt(Item::naoLidas).sum();
    }

    public int tamanho() {
        return itens.size();
    }

    public void limpar() {
        itens.clear();
    }

    /** Sala Geral sempre primeiro; depois mais recentes primeiro; sem atividade, por ordem alfabética. */
    private List<Item> ordenadas(boolean grupos) {
        List<Item> r = new ArrayList<>();
        for (Item i : itens.values()) {
            if (i.ehGrupo() == grupos) {
                r.add(i);
            }
        }
        r.sort(Comparator
                .comparing((Item i) -> !i.ehGeral())
                .thenComparing(Item::ultimaAtividade, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(i -> i.titulo().toLowerCase()));
        return r;
    }
}
