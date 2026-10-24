package model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsuarioTest {

    @Test
    void aceitaNomeValidoERemoveEspacosDasPontas() {
        assertEquals("Maria", new Usuario("  Maria ").getNome());
    }

    @Test
    void recusaNomeVazioOuNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario(""));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("   "));
        assertThrows(IllegalArgumentException.class, () -> new Usuario(null));
    }

    @Test
    void recusaNomeMuitoLongo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Usuario("x".repeat(Usuario.TAMANHO_MAXIMO_NOME + 1)));
    }

    @Test
    void recusaNomeReservadoSemImportarMaiusculas() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("Sistema"));
        assertThrows(IllegalArgumentException.class, () -> new Usuario("sIsTeMa"));
    }

    @Test
    void recusaCaracteresDeControle() {
        assertThrows(IllegalArgumentException.class, () -> new Usuario("Ana\nBia"));
    }

    @Test
    void usuariosComMesmoNomeSaoIguaisIgnorandoMaiusculas() {
        assertEquals(new Usuario("Ana"), new Usuario("ANA"));
        assertEquals(new Usuario("Ana").hashCode(), new Usuario("ANA").hashCode());
    }
}
