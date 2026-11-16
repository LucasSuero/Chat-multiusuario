package br.unicesumar.chat.server;

import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Comando;
import br.unicesumar.chat.protocol.Eventos;
import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.protocol.ProtocoloException;
import br.unicesumar.chat.service.ChatException;
import br.unicesumar.chat.service.Erro;
import br.unicesumar.chat.service.ServicoChat;
import br.unicesumar.chat.service.ValidacaoException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Traduz cada linha recebida em uma ação do ServicoChat/Roteador e devolve as respostas pela Sessao.
 * Não usa rede: recebe texto e escreve em uma Sessao, o que permite testar o protocolo inteiro em memória.
 */
public class ProcessadorComandos {

    private static final Logger LOG = Logger.getLogger(ProcessadorComandos.class.getName());

    private final ServicoChat servico;
    private final Roteador roteador;

    public ProcessadorComandos(ServicoChat servico, Roteador roteador) {
        this.servico = servico;
        this.roteador = roteador;
    }

    /** Processa uma linha. Retorna false quando a sessão deve ser encerrada (comando SAIR). */
    public boolean processar(Sessao sessao, String linha) {
        try {
            return executar(sessao, Protocolo.interpretar(linha));
        } catch (ChatException e) {
            sessao.enviar(Eventos.erro(e.erro(), e.getMessage()));
        } catch (ValidacaoException e) {
            sessao.enviar(Eventos.erro(Erro.VALIDACAO, e.getMessage()));
        } catch (ProtocoloException e) {
            sessao.enviar(Eventos.erro(Erro.COMANDO_INVALIDO, e.getMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Falha ao processar comando", e);
            sessao.enviar(Eventos.erro(Erro.ERRO_INTERNO, "Erro interno do servidor"));
        }
        return true;
    }

    private boolean executar(Sessao s, Comando c) {
        switch (c.tipo()) {
            case REGISTRAR -> {
                exigirAnonimo(s);
                Usuario u = servico.registrar(c.arg(0), c.arg(1).toCharArray(), c.arg(2));
                s.definirUsuario(u);
                s.enviar(Eventos.autenticado(u));
                enviarHistoricoGeral(s, u);
            }
            case LOGIN -> {
                exigirAnonimo(s);
                Usuario u = servico.autenticar(c.arg(0), c.arg(1).toCharArray());
                s.definirUsuario(u);
                s.enviar(Eventos.autenticado(u));
                enviarHistoricoGeral(s, u);
            }
            case SAIR -> {
                s.enviar(Eventos.ok("Até logo"));
                return false;
            }
            default -> executarAutenticado(exigirUsuario(s), s, c);
        }
        return true;
    }

    private void executarAutenticado(Usuario u, Sessao s, Comando c) {
        switch (c.tipo()) {
            case LISTAR_CONVERSAS -> {
                for (ConversaVisao v : servico.conversasDoUsuario(u)) {
                    s.enviar(Eventos.conversa(v));
                }
                s.enviar(Eventos.fimConversas());
            }
            case BUSCAR_USUARIOS -> enviarUsuarios(s, servico.buscarUsuarios(u, c.arg(0)));
            case ABRIR_PRIVADA -> {
                long outroId = c.argLong(0);
                ConversaVisao v = servico.abrirPrivada(u, outroId);
                s.enviar(Eventos.conversa(v));
                roteador.enviarAoUsuario(outroId, Eventos.conversaNova(new ConversaVisao(v.conversa(), u)));
            }
            case CRIAR_GRUPO -> {
                ConversaVisao v = servico.criarGrupo(u, c.arg(0), c.arg(1), c.argListaLong(2));
                s.enviar(Eventos.conversa(v));
                for (long id : servico.idsParticipantes(v.conversa().id())) {
                    if (id != u.id()) {
                        roteador.enviarAoUsuario(id, Eventos.conversaNova(v));
                    }
                }
            }
            case ADICIONAR_PARTICIPANTE -> {
                long novoId = c.argLong(1);
                var conversa = servico.adicionarParticipante(u, c.argLong(0), novoId);
                s.enviar(Eventos.ok("Participante adicionado"));
                roteador.enviarAoUsuario(novoId, Eventos.conversaNova(new ConversaVisao(conversa, null)));
            }
            case AVATAR -> {
                Usuario novo = servico.atualizarAvatar(u, c.arg(0));
                s.definirUsuario(novo);
                s.enviar(Eventos.perfilAtualizado(novo));
                roteador.propagarPerfil(novo, s);
            }
            case PARTICIPANTES -> enviarUsuarios(s, servico.participantes(u, c.argLong(0)));
            case ENTRAR -> {
                long conversaId = c.argLong(0);
                enviarHistorico(s, conversaId, servico.entrarNaConversa(u, conversaId));
            }
            case MSG -> roteador.enviar(u, c.argLong(0), c.arg(1));
            default -> throw new ProtocoloException("Comando não suportado");
        }
    }

    /** Ao autenticar, o cliente recebe sozinho as últimas mensagens da sala Geral (sem precisar pedir). */
    private void enviarHistoricoGeral(Sessao s, Usuario u) {
        enviarHistorico(s, servico.idGeral(), servico.historicoGeral(u));
    }

    private void enviarHistorico(Sessao s, long conversaId, List<Mensagem> historico) {
        s.enviar(Eventos.historicoInicio(conversaId));
        for (Mensagem m : historico) {
            s.enviar(Eventos.msg(m));
        }
        s.enviar(Eventos.historicoFim(conversaId));
    }

    private void enviarUsuarios(Sessao s, List<Usuario> usuarios) {
        for (Usuario usuario : usuarios) {
            s.enviar(Eventos.usuario(usuario));
        }
        s.enviar(Eventos.fimUsuarios());
    }

    private Usuario exigirUsuario(Sessao s) {
        return s.usuario().orElseThrow(() -> new ChatException(Erro.NAO_AUTENTICADO, "Faça login primeiro"));
    }

    private void exigirAnonimo(Sessao s) {
        if (s.usuario().isPresent()) {
            throw new ValidacaoException("Você já está autenticado");
        }
    }
}
