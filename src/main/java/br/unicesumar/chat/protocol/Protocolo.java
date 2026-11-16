package br.unicesumar.chat.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Protocolo de texto, uma linha por mensagem (UTF-8), campos separados por TAB.
 * Dentro de um campo: "\" vira "\\", TAB vira "\t", quebra de linha vira "\n" e "\r" vira "\r".
 * Assim o conteúdo das mensagens pode ter qualquer caractere sem quebrar o enquadramento.
 */
public final class Protocolo {

    public static final char SEPARADOR = '\t';
    public static final int MAX_LINHA = 16 * 1024;

    private Protocolo() {
    }

    /** Monta uma linha: nome + campos (null vira vazio). */
    public static String codificar(String nome, Object... campos) {
        StringBuilder sb = new StringBuilder(escapar(nome));
        for (Object campo : campos) {
            sb.append(SEPARADOR).append(escapar(campo == null ? "" : String.valueOf(campo)));
        }
        return sb.toString();
    }

    /** Divide uma linha nos seus campos, desfazendo o escape. Serve ao servidor e ao cliente. */
    public static List<String> dividir(String linha) {
        List<String> campos = new ArrayList<>();
        int inicio = 0;
        for (int i = 0; i <= linha.length(); i++) {
            if (i == linha.length() || linha.charAt(i) == SEPARADOR) {
                campos.add(desescapar(linha.substring(inicio, i)));
                inicio = i + 1;
            }
        }
        return campos;
    }

    /** Interpreta uma linha enviada pelo cliente. */
    public static Comando interpretar(String linha) {
        List<String> campos = dividir(linha);
        String nome = campos.get(0).strip().toUpperCase(Locale.ROOT);
        Comando.Tipo tipo;
        try {
            tipo = Comando.Tipo.valueOf(nome);
        } catch (IllegalArgumentException e) {
            throw new ProtocoloException("Comando desconhecido");
        }
        List<String> args = campos.subList(1, campos.size());
        if (args.size() != tipo.argumentos()) {
            throw new ProtocoloException(tipo + " espera " + tipo.argumentos() + " argumento(s)");
        }
        return new Comando(tipo, List.copyOf(args));
    }

    static String escapar(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String desescapar(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char prox = s.charAt(i + 1);
                switch (prox) {
                    case '\\' -> { sb.append('\\'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
