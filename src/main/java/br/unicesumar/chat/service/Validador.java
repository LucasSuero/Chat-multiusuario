package br.unicesumar.chat.service;

import java.util.regex.Pattern;

/** Regras de validação de entrada (sem rede e sem banco, fácil de testar). */
public final class Validador {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,20}");
    private static final Pattern IMAGEM = Pattern.compile("[a-z]{4,10}\\d{2}");
    public static final int SENHA_MINIMA = 6;
    public static final int NOME_GRUPO_MAXIMO = 40;

    private Validador() {
    }

    public static String username(String valor) {
        String v = valor == null ? "" : valor.strip();
        if (!USERNAME.matcher(v).matches()) {
            throw new ValidacaoException("Usuário deve ter 3 a 20 caracteres (letras, números, _ ou .)");
        }
        return v;
    }

    public static void senha(char[] senha) {
        if (senha == null || senha.length < SENHA_MINIMA) {
            throw new ValidacaoException("Senha deve ter ao menos " + SENHA_MINIMA + " caracteres");
        }
    }

    public static String nomeGrupo(String valor) {
        String v = valor == null ? "" : valor.strip();
        if (v.isEmpty() || v.length() > NOME_GRUPO_MAXIMO) {
            throw new ValidacaoException("Nome do grupo deve ter 1 a " + NOME_GRUPO_MAXIMO + " caracteres");
        }
        return v;
    }

    /** Avatar da galeria pré-definida (ex.: avatar01). Vazio vira o avatar padrão. */
    public static String avatar(String valor) {
        String v = valor == null ? "" : valor.strip();
        if (v.isEmpty()) {
            return "avatar01";
        }
        if (!IMAGEM.matcher(v).matches()) {
            throw new ValidacaoException("Avatar inválido");
        }
        return v;
    }

    /** Imagem opcional de grupo. Vazio vira null. */
    public static String imagemGrupo(String valor) {
        String v = valor == null ? "" : valor.strip();
        if (v.isEmpty()) {
            return null;
        }
        if (!IMAGEM.matcher(v).matches()) {
            throw new ValidacaoException("Imagem do grupo inválida");
        }
        return v;
    }
}
