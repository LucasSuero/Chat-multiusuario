package br.unicesumar.chat.protocol;

import java.util.ArrayList;
import java.util.List;

/** Comando enviado pelo cliente: tipo + argumentos já "desescapados". */
public record Comando(Tipo tipo, List<String> args) {

    /** Cada tipo declara quantos argumentos exige. */
    public enum Tipo {
        REGISTRAR(3),              // usuario, senha, avatar
        LOGIN(2),                  // usuario, senha
        LISTAR_CONVERSAS(0),
        BUSCAR_USUARIOS(1),        // termo
        ABRIR_PRIVADA(1),          // usuarioId
        CRIAR_GRUPO(3),            // nome, imagem, ids separados por vírgula
        ADICIONAR_PARTICIPANTE(2), // conversaId, usuarioId
        PARTICIPANTES(1),          // conversaId
        ENTRAR(1),                 // conversaId (devolve o histórico)
        MSG(2),                    // conversaId, texto
        AVATAR(1),                 // avatar (ex.: avatar05); persiste no banco
        SAIR(0);

        private final int argumentos;

        Tipo(int argumentos) {
            this.argumentos = argumentos;
        }

        public int argumentos() {
            return argumentos;
        }
    }

    private static final int MAX_IDS = 200;

    public String arg(int i) {
        return args.get(i);
    }

    public long argLong(int i) {
        try {
            return Long.parseLong(args.get(i).strip());
        } catch (NumberFormatException e) {
            throw new ProtocoloException("Argumento " + (i + 1) + " deve ser numérico");
        }
    }

    public List<Long> argListaLong(int i) {
        String v = args.get(i).strip();
        List<Long> ids = new ArrayList<>();
        if (v.isEmpty()) {
            return ids;
        }
        String[] partes = v.split(",");
        if (partes.length > MAX_IDS) {
            throw new ProtocoloException("Lista de ids grande demais");
        }
        for (String p : partes) {
            try {
                ids.add(Long.parseLong(p.strip()));
            } catch (NumberFormatException e) {
                throw new ProtocoloException("Lista de ids inválida");
            }
        }
        return ids;
    }
}
