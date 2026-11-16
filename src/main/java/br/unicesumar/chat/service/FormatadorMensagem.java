package br.unicesumar.chat.service;

import br.unicesumar.chat.model.Mensagem;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formatação textual de mensagens (independente de rede). */
public final class FormatadorMensagem {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private FormatadorMensagem() {
    }

    /** Ex.: "[14:05] ana: olá pessoal". */
    public static String formatar(Mensagem m, ZoneId zona) {
        return "[" + HORA.format(m.enviadaEm().atZone(zona)) + "] "
                + m.remetente().username() + ": " + m.conteudo();
    }
}
