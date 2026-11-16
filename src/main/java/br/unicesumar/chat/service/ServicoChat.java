package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.TipoConversa;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.UsuarioDuplicadoException;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.security.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras de negócio do chat, sem rede. Toda decisão de acesso ("quem pode ver/enviar o quê")
 * fica aqui: só participantes de uma conversa leem seu histórico ou enviam mensagens a ela.
 */
public class ServicoChat {

    public static final int HISTORICO_PADRAO = 50;
    private static final int TERMO_MAXIMO = 20;

    // Usado para gastar o mesmo tempo de hash quando o usuário não existe (evita revelar quem existe).
    private static final String HASH_FICTICIO = PasswordHasher.gerar("senha-ficticia".toCharArray());

    private final UsuarioRepository usuarios;
    private final ConversaRepository conversas;
    private final MensagemRepository mensagens;
    private final Clock clock;
    private volatile long idGeral;

    public ServicoChat(UsuarioRepository usuarios, ConversaRepository conversas,
                       MensagemRepository mensagens, Clock clock) {
        this.usuarios = usuarios;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.clock = clock;
    }

    // ---------- autenticação ----------

    public Usuario registrar(String username, char[] senha, String avatar) {
        String nome = Validador.username(username);
        Validador.senha(senha);
        String av = Validador.avatar(avatar);
        Usuario criado;
        try {
            criado = usuarios.criar(nome, PasswordHasher.gerar(senha), av);
        } catch (UsuarioDuplicadoException e) {
            throw new ChatException(Erro.USUARIO_EXISTE, "Esse nome de usuário já está em uso");
        }
        entrarNaGeral(criado);
        return criado;
    }

    public Usuario autenticar(String username, char[] senha) {
        String nome = username == null ? "" : username.strip();
        Optional<UsuarioRepository.Credencial> cred = usuarios.buscarCredencial(nome);
        boolean confere = PasswordHasher.verificar(senha, cred.map(UsuarioRepository.Credencial::senhaHash)
                .orElse(HASH_FICTICIO));
        if (cred.isEmpty() || !confere) {
            throw new ChatException(Erro.CREDENCIAIS_INVALIDAS, "Usuário ou senha inválidos");
        }
        Usuario autenticado = cred.get().usuario();
        entrarNaGeral(autenticado); // idempotente; também inclui usuários criados antes da sala Geral existir
        return autenticado;
    }

    // ---------- sala Geral (broadcast) ----------

    /** Id da sala Geral, da qual todo usuário participa: nela, cada mensagem vai a todos os conectados. */
    public long idGeral() {
        long id = idGeral;
        if (id == 0) {
            id = conversas.obterOuCriarGeral().id();
            idGeral = id;
        }
        return id;
    }

    /**
     * Histórico enviado ao conectar: usa o buscarUltimas(n) do MensagemRepository (últimas n mensagens da sala
     * Geral). Só participantes têm acesso; todo usuário autenticado já é participante da Geral.
     */
    public List<Mensagem> historicoGeral(Usuario usuario) {
        exigirParticipante(usuario, idGeral());
        return mensagens.buscarUltimas(HISTORICO_PADRAO);
    }

    private void entrarNaGeral(Usuario usuario) {
        conversas.adicionarParticipante(idGeral(), usuario.id());
    }

    /** Troca o avatar e grava no banco; mensagens futuras e logins seguintes já usam o novo avatar. */
    public Usuario atualizarAvatar(Usuario usuario, String avatar) {
        if (avatar == null || avatar.isBlank()) {
            throw new ValidacaoException("Escolha um avatar");
        }
        String valido = Validador.avatar(avatar);
        usuarios.atualizarAvatar(usuario.id(), valido);
        return new Usuario(usuario.id(), usuario.username(), valido);
    }

    // ---------- usuários e conversas ----------

    public List<Usuario> buscarUsuarios(Usuario solicitante, String termo) {
        String t = termo == null ? "" : termo.strip();
        if (t.isEmpty() || t.length() > TERMO_MAXIMO) {
            throw new ValidacaoException("Termo de busca deve ter de 1 a " + TERMO_MAXIMO + " caracteres");
        }
        return usuarios.pesquisar(t, solicitante.id());
    }

    public ConversaVisao abrirPrivada(Usuario solicitante, long outroId) {
        if (outroId == solicitante.id()) {
            throw new ValidacaoException("Não é possível abrir uma conversa consigo mesmo");
        }
        Usuario outro = usuarios.buscarPorId(outroId)
                .orElseThrow(() -> new ChatException(Erro.NAO_ENCONTRADO, "Usuário não encontrado"));
        return new ConversaVisao(conversas.obterOuCriarPrivada(solicitante.id(), outroId), outro);
    }

    public ConversaVisao criarGrupo(Usuario criador, String nome, String imagem, Collection<Long> participantes) {
        String nomeValido = Validador.nomeGrupo(nome);
        if (nomeValido.equalsIgnoreCase(Conversa.NOME_GERAL)) {
            throw new ValidacaoException("O nome \"" + Conversa.NOME_GERAL + "\" é reservado para a sala geral");
        }
        String imagemValida = Validador.imagemGrupo(imagem);
        Set<Long> ids = new LinkedHashSet<>(participantes);
        for (Long id : ids) {
            if (id != criador.id() && usuarios.buscarPorId(id).isEmpty()) {
                throw new ChatException(Erro.NAO_ENCONTRADO, "Usuário " + id + " não encontrado");
            }
        }
        Conversa grupo = conversas.criarGrupo(nomeValido, imagemValida, criador.id(), ids);
        return new ConversaVisao(grupo, null);
    }

    /** Qualquer participante de um grupo pode adicionar outro usuário. Conversas privadas não aceitam. */
    public Conversa adicionarParticipante(Usuario solicitante, long conversaId, long novoId) {
        Conversa conversa = exigirParticipante(solicitante, conversaId);
        if (conversa.tipo() != TipoConversa.GRUPO) {
            throw new ValidacaoException("Só é possível adicionar participantes a grupos");
        }
        if (usuarios.buscarPorId(novoId).isEmpty()) {
            throw new ChatException(Erro.NAO_ENCONTRADO, "Usuário não encontrado");
        }
        conversas.adicionarParticipante(conversaId, novoId);
        return conversa;
    }

    public List<ConversaVisao> conversasDoUsuario(Usuario usuario) {
        List<ConversaVisao> lista = new ArrayList<>();
        for (Conversa c : conversas.listarDoUsuario(usuario.id())) {
            Usuario parceiro = null;
            if (c.tipo() == TipoConversa.PRIVADA) {
                parceiro = conversas.listarParticipantes(c.id()).stream()
                        .filter(p -> p.id() != usuario.id()).findFirst().orElse(null);
            }
            lista.add(new ConversaVisao(c, parceiro));
        }
        return lista;
    }

    public List<Usuario> participantes(Usuario solicitante, long conversaId) {
        exigirParticipante(solicitante, conversaId);
        return conversas.listarParticipantes(conversaId);
    }

    public Set<Long> idsParticipantes(long conversaId) {
        return conversas.listarParticipantes(conversaId).stream().map(Usuario::id)
                .collect(Collectors.toSet());
    }

    // ---------- mensagens ----------

    /** Histórico (últimas mensagens) ao entrar em uma conversa. Só participantes têm acesso. */
    public List<Mensagem> entrarNaConversa(Usuario usuario, long conversaId) {
        exigirParticipante(usuario, conversaId);
        return mensagens.buscarUltimas(conversaId, HISTORICO_PADRAO);
    }

    /** Valida, verifica acesso e persiste. A entrega aos participantes é feita pelo Roteador. */
    public Mensagem enviarMensagem(Usuario remetente, long conversaId, String texto) {
        exigirParticipante(remetente, conversaId);
        Mensagem nova;
        try {
            nova = Mensagem.nova(conversaId, remetente, texto, Instant.now(clock));
        } catch (IllegalArgumentException e) {
            throw new ValidacaoException(e.getMessage());
        }
        return mensagens.salvar(nova);
    }

    private Conversa exigirParticipante(Usuario usuario, long conversaId) {
        Optional<Conversa> conversa = conversas.buscarPorId(conversaId);
        if (conversa.isEmpty() || !conversas.ehParticipante(conversaId, usuario.id())) {
            // Mesma resposta para "não existe" e "não participa": não revela conversas alheias.
            throw new ChatException(Erro.CONVERSA_INACESSIVEL, "Conversa não encontrada");
        }
        return conversa.get();
    }
}
