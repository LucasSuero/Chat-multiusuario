package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Uma mensagem do chat: remetente, conteúdo e horário.
 * É imutável e valida seus dados na criação.
 */
public final class Mensagem {

    public static final int TAMANHO_MAXIMO = 500;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final String remetente;
    private final String conteudo;
    private final LocalDateTime horario;

    public Mensagem(String remetente, String conteudo, LocalDateTime horario) {
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalArgumentException("A mensagem precisa ter um remetente.");
        }
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("A mensagem não pode ficar vazia.");
        }
        if (horario == null) {
            throw new IllegalArgumentException("A mensagem precisa ter um horário.");
        }
        if (conteudo.indexOf('\n') >= 0 || conteudo.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("A mensagem não pode ter quebra de linha.");
        }
        String limpo = conteudo.strip();
        if (limpo.length() > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException(
                    "A mensagem pode ter no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        this.remetente = remetente.strip();
        this.conteudo = limpo;
        // O MySQL (DATETIME(3)) guarda milissegundos; truncamos aqui para ler igual ao que gravou.
        this.horario = horario.truncatedTo(ChronoUnit.MILLIS);
    }

    /** Cria uma mensagem enviada agora. */
    public static Mensagem nova(String remetente, String conteudo) {
        return new Mensagem(remetente, conteudo, LocalDateTime.now());
    }

    /** Cria um aviso do próprio servidor (entrada/saída de usuários, erros). */
    public static Mensagem doSistema(String conteudo) {
        return nova(Usuario.NOME_RESERVADO, conteudo);
    }

    /** Texto exibido nas telas, por exemplo: "[14:35] Ana: oi, pessoal!" */
    public String formatar() {
        return "[" + FORMATO_HORA.format(horario) + "] " + remetente + ": " + conteudo;
    }

    public String getRemetente() {
        return remetente;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Mensagem m
                && remetente.equals(m.remetente)
                && conteudo.equals(m.conteudo)
                && horario.equals(m.horario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remetente, conteudo, horario);
    }

    @Override
    public String toString() {
        return formatar();
    }
}
