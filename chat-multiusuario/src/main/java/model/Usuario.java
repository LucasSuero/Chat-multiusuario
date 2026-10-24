package model;

import java.util.Locale;

/**
 * Representa quem participa do chat. Concentra as regras do nome de usuário.
 */
public final class Usuario {

    public static final int TAMANHO_MAXIMO_NOME = 20;
    public static final String NOME_RESERVADO = "Sistema";

    private final String nome;

    public Usuario(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome não pode ficar vazio.");
        }
        String limpo = nome.strip();
        if (limpo.length() > TAMANHO_MAXIMO_NOME) {
            throw new IllegalArgumentException(
                    "O nome pode ter no máximo " + TAMANHO_MAXIMO_NOME + " caracteres.");
        }
        if (limpo.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("O nome contém caracteres inválidos.");
        }
        if (limpo.equalsIgnoreCase(NOME_RESERVADO)) {
            throw new IllegalArgumentException("O nome \"" + NOME_RESERVADO + "\" é reservado.");
        }
        this.nome = limpo;
    }

    public String getNome() {
        return nome;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Usuario outro && nome.equalsIgnoreCase(outro.nome);
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return nome;
    }
}
