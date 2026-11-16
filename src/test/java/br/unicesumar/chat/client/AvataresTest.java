package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.service.Validador;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvataresTest {

    @Test
    void todosOsIdsDaGaleriaSaoAceitosPeloServidor() {
        for (String id : Avatares.idsUsuarios()) {
            assertEquals(id, Validador.avatar(id));
        }
        for (String id : Avatares.idsGrupos()) {
            assertEquals(id, Validador.imagemGrupo(id));
        }
    }

    @Test
    void galeriaTemIdsUnicosEPadroesExistem() {
        List<String> usuarios = Avatares.idsUsuarios();
        List<String> grupos = Avatares.idsGrupos();
        assertEquals(usuarios.size(), new HashSet<>(usuarios).size());
        assertEquals(grupos.size(), new HashSet<>(grupos).size());
        assertTrue(usuarios.contains(Avatares.PADRAO_USUARIO));
        assertTrue(grupos.contains(Avatares.PADRAO_GRUPO));
        assertTrue(usuarios.size() >= 12 && grupos.size() >= 8);
    }

    @Test
    void idDesconhecidoOuNuloUsaEstiloNeutroSemQuebrar() {
        assertEquals("desconhecido", Avatares.usuario("avatar99").id());
        assertEquals("desconhecido", Avatares.usuario(null).id());
        assertEquals("avatar03", Avatares.usuario("avatar03").id());
        assertEquals("grupo02", Avatares.grupo("grupo02").id());
    }

    @Test
    void coresSaoHexadecimaisValidas() {
        for (Avatares.Estilo e : java.util.stream.Stream.concat(Avatares.USUARIOS.stream(), Avatares.GRUPOS.stream()).toList()) {
            assertTrue(e.cor1().matches("#[0-9A-Fa-f]{6}"), e.id());
            assertTrue(e.cor2().matches("#[0-9A-Fa-f]{6}"), e.id());
        }
    }
}
