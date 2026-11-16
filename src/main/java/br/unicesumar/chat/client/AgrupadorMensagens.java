package br.unicesumar.chat.client;

import br.unicesumar.chat.model.Mensagem;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Decide separadores de dia e início de bloco (mesmo remetente em sequência) sem depender da interface. */
public final class AgrupadorMensagens {

    /** Mensagens do mesmo remetente com intervalo menor que este ficam no mesmo bloco. */
    public static final Duration INTERVALO_BLOCO = Duration.ofMinutes(5);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** separadorDia é null quando não há separador antes da mensagem. */
    public record Linha(Mensagem mensagem, boolean inicioBloco, String separadorDia) {
    }

    private AgrupadorMensagens() {
    }

    public static Linha calcular(Mensagem anterior, Mensagem atual, ZoneId zona, LocalDate hoje) {
        LocalDate diaAtual = atual.enviadaEm().atZone(zona).toLocalDate();
        String separador = null;
        if (anterior == null || !anterior.enviadaEm().atZone(zona).toLocalDate().equals(diaAtual)) {
            separador = rotuloDia(diaAtual, hoje);
        }
        boolean inicio = separador != null
                || anterior.remetente().id() != atual.remetente().id()
                || Duration.between(anterior.enviadaEm(), atual.enviadaEm()).compareTo(INTERVALO_BLOCO) > 0;
        return new Linha(atual, inicio, separador);
    }

    public static List<Linha> calcularTodas(List<Mensagem> mensagens, ZoneId zona, LocalDate hoje) {
        List<Linha> linhas = new ArrayList<>();
        Mensagem anterior = null;
        for (Mensagem m : mensagens) {
            linhas.add(calcular(anterior, m, zona, hoje));
            anterior = m;
        }
        return linhas;
    }

    public static String rotuloDia(LocalDate dia, LocalDate hoje) {
        if (dia.equals(hoje)) {
            return "Hoje";
        }
        if (dia.equals(hoje.minusDays(1))) {
            return "Ontem";
        }
        return DATA.format(dia);
    }
}
