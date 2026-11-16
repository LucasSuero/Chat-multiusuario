package br.unicesumar.chat.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Galeria de avatares (usuários) e imagens de grupo pré-definidas. Os ids (avatar01, grupo01...)
 * são o que trafega no protocolo e fica no banco; a aparência é desenhada pela interface.
 */
public final class Avatares {

    /** Motivo geométrico desenhado sobre o fundo em degradê. */
    public enum Motivo { TRIANGULO, LOSANGO, QUADRADO, ANEL, CRUZ, BARRAS }

    public record Estilo(String id, String cor1, String cor2, Motivo motivo) {
    }

    public static final String PADRAO_USUARIO = "avatar01";
    public static final String PADRAO_GRUPO = "grupo01";

    public static final List<Estilo> USUARIOS = List.of(
            new Estilo("avatar01", "#7C5CFF", "#B79CFF", Motivo.TRIANGULO),
            new Estilo("avatar02", "#00B894", "#55EFC4", Motivo.ANEL),
            new Estilo("avatar03", "#0984E3", "#74B9FF", Motivo.LOSANGO),
            new Estilo("avatar04", "#E17055", "#FAB1A0", Motivo.CRUZ),
            new Estilo("avatar05", "#E84393", "#FD79A8", Motivo.QUADRADO),
            new Estilo("avatar06", "#F9A825", "#FFD54F", Motivo.BARRAS),
            new Estilo("avatar07", "#2D3436", "#636E72", Motivo.TRIANGULO),
            new Estilo("avatar08", "#00B5B0", "#81ECEC", Motivo.LOSANGO),
            new Estilo("avatar09", "#D63031", "#FF7675", Motivo.ANEL),
            new Estilo("avatar10", "#5F27CD", "#A55EEA", Motivo.CRUZ),
            new Estilo("avatar11", "#1E90FF", "#00D2D3", Motivo.BARRAS),
            new Estilo("avatar12", "#FF9F43", "#FF6B6B", Motivo.QUADRADO));

    public static final List<Estilo> GRUPOS = List.of(
            new Estilo("grupo01", "#6C5CE7", "#A29BFE", Motivo.ANEL),
            new Estilo("grupo02", "#00B894", "#00CEC9", Motivo.QUADRADO),
            new Estilo("grupo03", "#FD79A8", "#E84393", Motivo.LOSANGO),
            new Estilo("grupo04", "#0984E3", "#6C5CE7", Motivo.BARRAS),
            new Estilo("grupo05", "#FDCB6E", "#E17055", Motivo.TRIANGULO),
            new Estilo("grupo06", "#2D3436", "#636E72", Motivo.CRUZ),
            new Estilo("grupo07", "#55EFC4", "#0984E3", Motivo.ANEL),
            new Estilo("grupo08", "#FF7675", "#D63031", Motivo.LOSANGO));

    private static final Estilo NEUTRO = new Estilo("desconhecido", "#636E72", "#B2BEC3", Motivo.ANEL);

    private Avatares() {
    }

    public static Estilo usuario(String id) {
        return buscar(USUARIOS, id);
    }

    public static Estilo grupo(String id) {
        return buscar(GRUPOS, id);
    }

    public static List<String> idsUsuarios() {
        return ids(USUARIOS);
    }

    public static List<String> idsGrupos() {
        return ids(GRUPOS);
    }

    private static Estilo buscar(List<Estilo> lista, String id) {
        if (id != null) {
            for (Estilo e : lista) {
                if (e.id().equals(id)) {
                    return e;
                }
            }
        }
        return NEUTRO; // id desconhecido (ex.: avatar de uma versão futura) não pode quebrar a tela
    }

    private static List<String> ids(List<Estilo> lista) {
        List<String> r = new ArrayList<>();
        for (Estilo e : lista) {
            r.add(e.id());
        }
        return r;
    }
}
