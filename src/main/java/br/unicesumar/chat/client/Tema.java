package br.unicesumar.chat.client;

/** Tema visual. O nome é gravado nas preferências; o arquivo CSS define as cores do tema. */
public enum Tema {
    CLARO("/css/tema-claro.css"),
    ESCURO("/css/tema-escuro.css");

    private final String recursoCss;

    Tema(String recursoCss) {
        this.recursoCss = recursoCss;
    }

    public String recursoCss() {
        return recursoCss;
    }

    public Tema alternar() {
        return this == CLARO ? ESCURO : CLARO;
    }

    public static Tema deNome(String nome, Tema padrao) {
        if (nome != null) {
            for (Tema t : values()) {
                if (t.name().equalsIgnoreCase(nome.strip())) {
                    return t;
                }
            }
        }
        return padrao;
    }
}
