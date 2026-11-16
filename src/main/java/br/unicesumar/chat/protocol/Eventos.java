package br.unicesumar.chat.protocol;

import br.unicesumar.chat.model.Conversa;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.service.Erro;

/** Linhas que o servidor envia aos clientes. */
public final class Eventos {

    private Eventos() {
    }

    public static String ok(String detalhe) {
        return Protocolo.codificar("OK", detalhe);
    }

    public static String erro(Erro codigo, String mensagem) {
        return Protocolo.codificar("ERRO", codigo.name(), mensagem);
    }

    public static String autenticado(Usuario u) {
        return Protocolo.codificar("AUTENTICADO", u.id(), u.username(), u.avatar());
    }

    /** PERFIL_ATUALIZADO id usuario avatar: enviado a todas as sessões do usuário após trocar o avatar. */
    public static String perfilAtualizado(Usuario u) {
        return Protocolo.codificar("PERFIL_ATUALIZADO", u.id(), u.username(), u.avatar());
    }

    public static String usuario(Usuario u) {
        return Protocolo.codificar("USUARIO", u.id(), u.username(), u.avatar());
    }

    public static String fimUsuarios() {
        return Protocolo.codificar("FIM_USUARIOS");
    }

    /** CONVERSA id tipo nome imagem parceiroId parceiroNome parceiroAvatar (parceiro só em privadas). */
    public static String conversa(ConversaVisao v) {
        return conversaComo("CONVERSA", v);
    }

    /** Mesmo formato de CONVERSA, enviado sem pedido quando o usuário passa a fazer parte de uma conversa. */
    public static String conversaNova(ConversaVisao v) {
        return conversaComo("CONVERSA_NOVA", v);
    }

    public static String fimConversas() {
        return Protocolo.codificar("FIM_CONVERSAS");
    }

    public static String historicoInicio(long conversaId) {
        return Protocolo.codificar("HISTORICO_INICIO", conversaId);
    }

    public static String historicoFim(long conversaId) {
        return Protocolo.codificar("HISTORICO_FIM", conversaId);
    }

    /** MSG id conversaId remetenteId remetenteNome remetenteAvatar instante conteudo (histórico e tempo real). */
    public static String msg(Mensagem m) {
        Usuario r = m.remetente();
        return Protocolo.codificar("MSG", m.id(), m.conversaId(), r.id(), r.username(), r.avatar(),
                m.enviadaEm(), m.conteudo());
    }

    private static String conversaComo(String evento, ConversaVisao v) {
        Conversa c = v.conversa();
        Usuario p = v.parceiro();
        return Protocolo.codificar(evento, c.id(), c.tipo(), c.nome(), c.imagem(),
                p == null ? null : p.id(), p == null ? null : p.username(), p == null ? null : p.avatar());
    }
}
