package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.testsupport.SessaoFalsa;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegistroSessoesTest {

    private final Usuario ana = new Usuario(1, "ana", null);
    private final Usuario bruno = new Usuario(2, "bruno", null);

    @Test
    void entradaESaidaDeSessoes() {
        RegistroSessoes registro = new RegistroSessoes();
        SessaoFalsa s1 = new SessaoFalsa(ana);
        SessaoFalsa s2 = new SessaoFalsa(bruno);
        registro.adicionar(s1);
        registro.adicionar(s2);
        assertEquals(2, registro.total());
        assertTrue(registro.remover(s1));
        assertEquals(1, registro.total());
        assertFalse(registro.remover(s1), "remover duas vezes não pode dar erro nem alterar a lista");
        assertFalse(registro.usuarioOnline(1));
        assertTrue(registro.usuarioOnline(2));
    }

    @Test
    void mesmoUsuarioPodeTerVariasSessoes() {
        RegistroSessoes registro = new RegistroSessoes();
        SessaoFalsa celular = new SessaoFalsa(ana);
        SessaoFalsa notebook = new SessaoFalsa(ana);
        registro.adicionar(celular);
        registro.adicionar(notebook);
        assertEquals(2, registro.sessoesDoUsuario(1).size());
        assertEquals(Set.of(1L), registro.usuariosOnline());
        registro.remover(celular);
        assertTrue(registro.usuarioOnline(1), "ainda há uma sessão ativa");
        registro.remover(notebook);
        assertFalse(registro.usuarioOnline(1));
    }

    @Test
    void sessaoSemLoginNaoContaComoUsuarioOnline() {
        RegistroSessoes registro = new RegistroSessoes();
        SessaoFalsa anonima = new SessaoFalsa();
        registro.adicionar(anonima);
        assertEquals(1, registro.total());
        assertTrue(registro.usuariosOnline().isEmpty());
        anonima.definirUsuario(ana);
        assertTrue(registro.usuarioOnline(1));
    }

    @Test
    void adicoesConcorrentesNaoPerdemSessoes() throws Exception {
        RegistroSessoes registro = new RegistroSessoes();
        int threads = 8;
        int porThread = 500;
        List<Thread> lista = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < porThread; i++) {
                    registro.adicionar(new SessaoFalsa(ana));
                }
            });
            lista.add(th);
            th.start();
        }
        for (Thread th : lista) {
            th.join();
        }
        assertEquals(threads * porThread, registro.total());
    }

    @Test
    void snapshotNaoSofreComModificacoesPosteriores() {
        RegistroSessoes registro = new RegistroSessoes();
        SessaoFalsa s = new SessaoFalsa(ana);
        registro.adicionar(s);
        List<Sessao> copia = registro.snapshot();
        registro.remover(s);
        assertEquals(1, copia.size());
        assertEquals(0, registro.total());
    }
}
