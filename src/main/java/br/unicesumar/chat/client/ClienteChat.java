package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoConversa;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Cliente do chat, independente de JavaFX. Fala exatamente o protocolo do servidor (reutiliza
 * {@link Protocolo}), mantém o estado da sessão (usuário, conversas, mensagens) e avisa um
 * {@link Ouvinte} a cada novidade.
 *
 * Os métodos de ação nunca bloqueiam (só enfileiram o envio), exceto {@link #conectar} e
 * {@link #reconectar}, que abrem o socket. Os callbacks do Ouvinte rodam na thread de leitura da
 * rede: quem usa uma interface gráfica deve repassar para a thread da interface.
 */
public final class ClienteChat {

    public interface Ouvinte {
        default void onAutenticado(Usuario usuario) {
        }

        default void onPerfilAtualizado(Usuario usuario) {
        }

        /** Conversa recebida: resposta a um pedido (nova=false) ou aviso do servidor (nova=true). */
        default void onConversa(ConversaVisao conversa, boolean nova) {
        }

        default void onConversasCarregadas(List<ConversaVisao> conversas) {
        }

        default void onHistoricoCarregado(long conversaId) {
        }

        /** Mensagem ainda não vista (do histórico ou em tempo real). Repetidas são descartadas. */
        default void onMensagem(Mensagem mensagem) {
        }

        default void onUsuariosEncontrados(String termo, List<Usuario> usuarios) {
        }

        default void onParticipantes(long conversaId, List<Usuario> participantes) {
        }

        default void onOk(String detalhe) {
        }

        default void onErro(String codigo, String mensagem) {
        }

        /** A conexão caiu (não é chamado quando o próprio cliente encerra). */
        default void onDesconectado(String motivo) {
        }
    }

    private record Pendente(boolean busca, String termo, long conversaId) {
    }

    private static final Logger LOG = Logger.getLogger(ClienteChat.class.getName());
    private static final int TIMEOUT_CONEXAO_MS = 5000;
    private static final int MAX_MENSAGENS_POR_CONVERSA = 500;

    private volatile Ouvinte ouvinte = new Ouvinte() {
    };

    private final ConexaoServidor.Ouvinte ponte = new ConexaoServidor.Ouvinte() {
        @Override
        public void aoReceber(ConexaoServidor origem, String linha) {
            if (ehAtual(origem)) {
                receber(linha);
            }
        }

        @Override
        public void aoPerder(ConexaoServidor origem, String motivo) {
            if (ehAtual(origem)) {
                ouvinte.onDesconectado(motivo);
            }
        }
    };

    // Estado protegido por "this".
    private ConexaoServidor conexao;
    private String host;
    private int porta;
    private Usuario usuario;
    private String tentativaUsuario;
    private char[] tentativaSenha;
    private String credUsuario;
    private char[] credSenha;
    private final Map<Long, ConversaVisao> conversas = new LinkedHashMap<>();
    private final Map<Long, NavigableMap<Long, Mensagem>> mensagens = new HashMap<>();
    private final List<Usuario> listaEmAndamento = new ArrayList<>();
    private final Deque<Pendente> pendentes = new ArrayDeque<>();

    public void definirOuvinte(Ouvinte novo) {
        this.ouvinte = novo == null ? new Ouvinte() {
        } : novo;
    }

    // ------------------------------------------------------------------ conexão

    /** Abre a conexão com o servidor (bloqueia até conectar ou falhar). */
    public void conectar(String host, int porta) throws IOException {
        ConexaoServidor nova = new ConexaoServidor(ponte);
        nova.abrir(host, porta, TIMEOUT_CONEXAO_MS);
        ConexaoServidor antiga;
        synchronized (this) {
            antiga = conexao;
            conexao = nova;
            this.host = host;
            this.porta = porta;
            pendentes.clear();
            listaEmAndamento.clear();
        }
        if (antiga != null) {
            antiga.fechar();
        }
        nova.iniciar();
    }

    /** Reabre a conexão e refaz o login com as credenciais da última autenticação bem-sucedida. */
    public void reconectar() throws IOException {
        String h;
        int p;
        String u;
        String s;
        synchronized (this) {
            if (credUsuario == null || host == null) {
                throw new IllegalStateException("Nenhuma sessão anterior para reconectar");
            }
            h = host;
            p = porta;
            u = credUsuario;
            s = new String(credSenha);
        }
        conectar(h, p);
        login(u, s);
    }

    public synchronized boolean conectado() {
        return conexao != null && conexao.aberta();
    }

    public synchronized boolean autenticado() {
        return usuario != null;
    }

    /** Encerra a sessão: avisa o servidor, fecha a conexão e esquece credenciais e estado. */
    public void sair() {
        ConexaoServidor c;
        synchronized (this) {
            c = conexao;
            conexao = null;
            usuario = null;
            tentativaUsuario = null;
            tentativaSenha = null;
            credUsuario = null;
            credSenha = null;
            conversas.clear();
            mensagens.clear();
            pendentes.clear();
            listaEmAndamento.clear();
        }
        if (c != null) {
            if (c.aberta()) {
                c.enviar(Protocolo.codificar("SAIR"));
            }
            c.fechar();
        }
    }

    // ------------------------------------------------------------------ ações

    public void login(String usuario, String senha) {
        guardarTentativa(usuario, senha);
        enviar("LOGIN", usuario, senha);
    }

    public void registrar(String usuario, String senha, String avatar) {
        guardarTentativa(usuario, senha);
        enviar("REGISTRAR", usuario, senha, avatar);
    }

    public void carregarConversas() {
        enviar("LISTAR_CONVERSAS");
    }

    /** Pede o histórico da conversa; as mensagens chegam por onMensagem e termina em onHistoricoCarregado. */
    public void entrarNaConversa(long conversaId) {
        enviar("ENTRAR", conversaId);
    }

    public void enviarMensagem(long conversaId, String texto) {
        String t = texto == null ? "" : texto.strip();
        if (t.isEmpty()) {
            return;
        }
        if (t.length() > Mensagem.TAMANHO_MAXIMO) {
            ouvinte.onErro("VALIDACAO", "A mensagem excede " + Mensagem.TAMANHO_MAXIMO + " caracteres");
            return;
        }
        enviar("MSG", conversaId, t);
    }

    public void abrirPrivada(long usuarioId) {
        enviar("ABRIR_PRIVADA", usuarioId);
    }

    public void criarGrupo(String nome, String imagem, Collection<Long> participantes) {
        String ids = participantes.stream().map(String::valueOf).collect(Collectors.joining(","));
        enviar("CRIAR_GRUPO", nome, imagem == null ? "" : imagem, ids);
    }

    public void adicionarParticipante(long conversaId, long usuarioId) {
        enviar("ADICIONAR_PARTICIPANTE", conversaId, usuarioId);
    }

    public void buscarUsuarios(String termo) {
        String t = termo == null ? "" : termo.strip();
        if (t.isEmpty()) {
            return;
        }
        synchronized (this) {
            pendentes.addLast(new Pendente(true, t, 0));
        }
        enviar("BUSCAR_USUARIOS", t);
    }

    public void participantes(long conversaId) {
        synchronized (this) {
            pendentes.addLast(new Pendente(false, "", conversaId));
        }
        enviar("PARTICIPANTES", conversaId);
    }

    public void alterarAvatar(String avatar) {
        enviar("AVATAR", avatar);
    }

    // ------------------------------------------------------------------ estado (cópias)

    public synchronized Optional<Usuario> usuario() {
        return Optional.ofNullable(usuario);
    }

    public synchronized List<ConversaVisao> conversas() {
        return List.copyOf(conversas.values());
    }

    /** Mensagens já recebidas da conversa, em ordem cronológica. */
    public synchronized List<Mensagem> mensagens(long conversaId) {
        NavigableMap<Long, Mensagem> m = mensagens.get(conversaId);
        return m == null ? List.of() : List.copyOf(m.values());
    }

    public synchronized String host() {
        return host;
    }

    public synchronized int porta() {
        return porta;
    }

    // ------------------------------------------------------------------ internos

    private synchronized void guardarTentativa(String usuario, String senha) {
        tentativaUsuario = usuario;
        tentativaSenha = senha.toCharArray();
    }

    private synchronized boolean ehAtual(ConexaoServidor c) {
        return c == conexao;
    }

    private void enviar(String comando, Object... campos) {
        ConexaoServidor c;
        synchronized (this) {
            c = conexao;
        }
        if (c == null || !c.aberta()) {
            ouvinte.onErro("SEM_CONEXAO", "Sem conexão com o servidor");
            return;
        }
        c.enviar(Protocolo.codificar(comando, campos));
    }

    private void receber(String linha) {
        List<String> c = Protocolo.dividir(linha);
        try {
            tratar(c);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Linha do servidor ignorada: " + linha, e);
        }
    }

    private void tratar(List<String> c) {
        Ouvinte o = ouvinte;
        switch (c.get(0)) {
            case "AUTENTICADO" -> {
                Usuario u = usuarioDe(c, 1);
                synchronized (this) {
                    usuario = u;
                    if (tentativaUsuario != null) {
                        credUsuario = tentativaUsuario;
                        credSenha = tentativaSenha;
                    }
                }
                o.onAutenticado(u);
            }
            case "PERFIL_ATUALIZADO" -> {
                Usuario u = usuarioDe(c, 1);
                synchronized (this) {
                    usuario = u;
                }
                o.onPerfilAtualizado(u);
            }
            case "CONVERSA", "CONVERSA_NOVA" -> {
                ConversaVisao v = conversaDe(c);
                synchronized (this) {
                    conversas.put(v.conversa().id(), v);
                }
                o.onConversa(v, c.get(0).equals("CONVERSA_NOVA"));
            }
            case "FIM_CONVERSAS" -> o.onConversasCarregadas(conversas());
            case "USUARIO" -> {
                synchronized (this) {
                    listaEmAndamento.add(usuarioDe(c, 1));
                }
            }
            case "FIM_USUARIOS" -> {
                Pendente p;
                List<Usuario> lista;
                synchronized (this) {
                    p = pendentes.pollFirst();
                    lista = List.copyOf(listaEmAndamento);
                    listaEmAndamento.clear();
                }
                if (p != null) {
                    if (p.busca()) {
                        o.onUsuariosEncontrados(p.termo(), lista);
                    } else {
                        o.onParticipantes(p.conversaId(), lista);
                    }
                }
            }
            case "HISTORICO_FIM" -> o.onHistoricoCarregado(Long.parseLong(c.get(1)));
            case "HISTORICO_INICIO" -> {
                // marcador de início: nada a fazer, as mensagens são tratadas uma a uma
            }
            case "MSG" -> {
                Mensagem m = mensagemDe(c);
                if (guardar(m)) {
                    o.onMensagem(m);
                }
            }
            case "OK" -> o.onOk(c.size() > 1 ? c.get(1) : "");
            case "ERRO" -> o.onErro(c.size() > 1 ? c.get(1) : "ERRO", c.size() > 2 ? c.get(2) : "");
            default -> LOG.fine("Evento desconhecido ignorado: " + c.get(0));
        }
    }

    /** Guarda a mensagem (ordenada por id, sem repetição). Retorna false se já era conhecida. */
    private synchronized boolean guardar(Mensagem m) {
        NavigableMap<Long, Mensagem> da = mensagens.computeIfAbsent(m.conversaId(), k -> new TreeMap<>());
        if (da.containsKey(m.id())) {
            return false;
        }
        da.put(m.id(), m);
        while (da.size() > MAX_MENSAGENS_POR_CONVERSA) {
            da.pollFirstEntry();
        }
        return true;
    }

    private static Usuario usuarioDe(List<String> c, int i) {
        return new Usuario(Long.parseLong(c.get(i)), c.get(i + 1), c.get(i + 2));
    }

    private static ConversaVisao conversaDe(List<String> c) {
        // CONVERSA id tipo nome imagem parceiroId parceiroNome parceiroAvatar
        Conversa conversa = new Conversa(Long.parseLong(c.get(1)), TipoConversa.valueOf(c.get(2)),
                vazioParaNulo(c.get(3)), vazioParaNulo(c.get(4)), null);
        Usuario parceiro = c.get(5).isEmpty() ? null : usuarioDe(c, 5);
        return new ConversaVisao(conversa, parceiro);
    }

    private static Mensagem mensagemDe(List<String> c) {
        // MSG id conversaId remetenteId remetenteNome remetenteAvatar instante conteudo
        return new Mensagem(Long.parseLong(c.get(1)), Long.parseLong(c.get(2)),
                new Usuario(Long.parseLong(c.get(3)), c.get(4), c.get(5)), c.get(7), Instant.parse(c.get(6)));
    }

    private static String vazioParaNulo(String s) {
        return s.isEmpty() ? null : s;
    }
}
