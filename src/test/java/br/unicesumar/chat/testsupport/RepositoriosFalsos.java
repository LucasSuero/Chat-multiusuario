package br.unicesumar.chat.testsupport;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoConversa;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.UsuarioDuplicadoException;
import br.unicesumar.chat.persistence.UsuarioRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositórios em memória para testar a lógica do servidor sem banco e sem rede.
 * Estendem os repositórios reais (que nunca tocam no Database passado como null,
 * porque todos os métodos usados aqui são sobrescritos). Seguros para uso entre threads.
 */
public final class RepositoriosFalsos {

    public final UsuariosFalso usuarios = new UsuariosFalso();
    public final ConversasFalso conversas = new ConversasFalso(usuarios);
    public final MensagensFalso mensagens = new MensagensFalso();

    public RepositoriosFalsos() {
        mensagens.definirGeral(() -> conversas.obterOuCriarGeral().id());
    }

    public static final class UsuariosFalso extends UsuarioRepository {
        private final Map<Long, Usuario> porId = new LinkedHashMap<>();
        private final Map<Long, String> hashes = new HashMap<>();
        private final AtomicLong seq = new AtomicLong();

        public UsuariosFalso() {
            super(null);
        }

        @Override
        public synchronized Usuario criar(String username, String senhaHash, String avatar) {
            for (Usuario u : porId.values()) {
                if (u.username().equalsIgnoreCase(username)) {
                    throw new UsuarioDuplicadoException(username);
                }
            }
            Usuario u = new Usuario(seq.incrementAndGet(), username, avatar);
            porId.put(u.id(), u);
            hashes.put(u.id(), senhaHash);
            return u;
        }

        @Override
        public synchronized Optional<UsuarioRepository.Credencial> buscarCredencial(String username) {
            for (Usuario u : porId.values()) {
                if (u.username().equalsIgnoreCase(username)) {
                    return Optional.of(new UsuarioRepository.Credencial(u, hashes.get(u.id())));
                }
            }
            return Optional.empty();
        }

        @Override
        public synchronized Optional<Usuario> buscarPorId(long id) {
            return Optional.ofNullable(porId.get(id));
        }

        @Override
        public synchronized List<Usuario> pesquisar(String termo, long excluirId) {
            List<Usuario> r = new ArrayList<>();
            for (Usuario u : porId.values()) {
                if (u.id() != excluirId && u.username().toLowerCase().contains(termo.toLowerCase())) {
                    r.add(u);
                }
            }
            r.sort(Comparator.comparing(Usuario::username));
            return r;
        }

        @Override
        public synchronized void atualizarAvatar(long id, String avatar) {
            Usuario u = porId.get(id);
            if (u != null) {
                porId.put(id, new Usuario(id, u.username(), avatar));
            }
        }

        public synchronized String hashDe(long id) {
            return hashes.get(id);
        }
    }

    public static final class ConversasFalso extends ConversaRepository {
        private final UsuariosFalso usuarios;
        private final Map<Long, Conversa> porId = new LinkedHashMap<>();
        private final Map<Long, Set<Long>> membros = new HashMap<>();
        private final AtomicLong seq = new AtomicLong();

        ConversasFalso(UsuariosFalso usuarios) {
            super(null);
            this.usuarios = usuarios;
        }

        private Conversa criar(TipoConversa tipo, String nome, String imagem, long criador, Collection<Long> ids) {
            Conversa c = new Conversa(seq.incrementAndGet(), tipo, nome, imagem, criador);
            porId.put(c.id(), c);
            membros.put(c.id(), new LinkedHashSet<>(ids));
            return c;
        }

        @Override
        public synchronized Conversa obterOuCriarPrivada(long a, long b) {
            if (a == b) {
                throw new IllegalArgumentException("Conversa privada exige dois usuários diferentes");
            }
            for (Conversa c : porId.values()) {
                Set<Long> m = membros.get(c.id());
                if (c.tipo() == TipoConversa.PRIVADA && m.contains(a) && m.contains(b)) {
                    return c;
                }
            }
            return criar(TipoConversa.PRIVADA, null, null, a, List.of(a, b));
        }

        @Override
        public synchronized Conversa obterOuCriarGeral() {
            for (Conversa c : porId.values()) {
                if (c.tipo() == TipoConversa.GRUPO && c.criadorId() == null && Conversa.NOME_GERAL.equals(c.nome())) {
                    return c;
                }
            }
            Conversa geral = new Conversa(seq.incrementAndGet(), TipoConversa.GRUPO, Conversa.NOME_GERAL, "grupo01", null);
            porId.put(geral.id(), geral);
            membros.put(geral.id(), new LinkedHashSet<>());
            return geral;
        }

        @Override
        public synchronized Conversa criarGrupo(String nome, String imagem, long criadorId, Collection<Long> participantes) {
            Set<Long> todos = new LinkedHashSet<>(participantes);
            todos.add(criadorId);
            return criar(TipoConversa.GRUPO, nome, imagem, criadorId, todos);
        }

        @Override
        public synchronized void adicionarParticipante(long conversaId, long usuarioId) {
            membros.get(conversaId).add(usuarioId);
        }

        @Override
        public synchronized boolean ehParticipante(long conversaId, long usuarioId) {
            Set<Long> m = membros.get(conversaId);
            return m != null && m.contains(usuarioId);
        }

        @Override
        public synchronized List<Usuario> listarParticipantes(long conversaId) {
            List<Usuario> r = new ArrayList<>();
            for (long id : membros.getOrDefault(conversaId, Set.of())) {
                usuarios.buscarPorId(id).ifPresent(r::add);
            }
            r.sort(Comparator.comparing(Usuario::username));
            return r;
        }

        @Override
        public synchronized List<Conversa> listarDoUsuario(long usuarioId) {
            List<Conversa> r = new ArrayList<>();
            for (Conversa c : porId.values()) {
                if (membros.get(c.id()).contains(usuarioId)) {
                    r.add(c);
                }
            }
            return r;
        }

        @Override
        public synchronized Optional<Conversa> buscarPorId(long id) {
            return Optional.ofNullable(porId.get(id));
        }
    }

    public static final class MensagensFalso extends MensagemRepository {
        private final List<Mensagem> todas = new ArrayList<>();
        private final AtomicLong seq = new AtomicLong();
        private java.util.function.LongSupplier geral = () -> 0;

        MensagensFalso() {
            super(null);
        }

        void definirGeral(java.util.function.LongSupplier idGeral) {
            this.geral = idGeral;
        }

        @Override
        public synchronized List<Mensagem> buscarUltimas(int n) {
            return buscarUltimas(geral.getAsLong(), n);
        }

        @Override
        public synchronized Mensagem salvar(Mensagem m) {
            Mensagem salva = m.comId(seq.incrementAndGet());
            todas.add(salva);
            return salva;
        }

        @Override
        public synchronized List<Mensagem> buscarUltimas(long conversaId, int n) {
            List<Mensagem> daConversa = new ArrayList<>();
            for (Mensagem m : todas) {
                if (m.conversaId() == conversaId) {
                    daConversa.add(m);
                }
            }
            return new ArrayList<>(daConversa.subList(Math.max(0, daConversa.size() - n), daConversa.size()));
        }

        public synchronized int total() {
            return todas.size();
        }
    }
}
