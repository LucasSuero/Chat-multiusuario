package server;

import model.Protocolo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerenciadorClientesTest {

    /** Cliente de mentira: guarda o que recebeu, sem usar rede. */
    static class ClienteFalso implements Destinatario {
        private final String nome;
        private final boolean falha;
        private final List<String> recebidas = new ArrayList<>();
        private boolean fechado;

        ClienteFalso(String nome) {
            this(nome, false);
        }

        ClienteFalso(String nome, boolean falha) {
            this.nome = nome;
            this.falha = falha;
        }

        @Override
        public String getNome() {
            return nome;
        }

        @Override
        public synchronized boolean enviar(String texto) {
            if (falha) {
                return false;
            }
            recebidas.add(texto);
            return true;
        }

        @Override
        public synchronized void fechar() {
            fechado = true;
        }

        synchronized List<String> recebidas() {
            return new ArrayList<>(recebidas);
        }

        synchronized boolean fechado() {
            return fechado;
        }
    }

    @Test
    void registrarConfirmaComOkAntesDeQualquerMensagem() {
        GerenciadorClientes g = new GerenciadorClientes();
        ClienteFalso ana = new ClienteFalso("Ana");
        assertTrue(g.registrar(ana));
        g.broadcast("oi");
        assertEquals(List.of(Protocolo.OK, "oi"), ana.recebidas());
    }

    @Test
    void broadcastChegaATodosOsClientes() {
        GerenciadorClientes g = new GerenciadorClientes();
        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        ClienteFalso caio = new ClienteFalso("Caio");
        g.registrar(ana);
        g.registrar(bia);
        g.registrar(caio);

        assertEquals(3, g.broadcast("olá, turma"));
        assertTrue(ana.recebidas().contains("olá, turma"));
        assertTrue(bia.recebidas().contains("olá, turma"));
        assertTrue(caio.recebidas().contains("olá, turma"));
    }

    @Test
    void recusaNomeJaEmUsoIgnorandoMaiusculas() {
        GerenciadorClientes g = new GerenciadorClientes();
        assertTrue(g.registrar(new ClienteFalso("Ana")));
        ClienteFalso repetida = new ClienteFalso("ANA");
        assertFalse(g.registrar(repetida));
        assertEquals(1, g.quantidade());
        assertTrue(repetida.recebidas().isEmpty());
    }

    @Test
    void clienteRemovidoParaDeReceber() {
        GerenciadorClientes g = new GerenciadorClientes();
        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        g.registrar(ana);
        g.registrar(bia);

        assertTrue(g.remover(ana));
        g.broadcast("só pra quem ficou");

        assertFalse(ana.recebidas().contains("só pra quem ficou"));
        assertTrue(bia.recebidas().contains("só pra quem ficou"));
        assertEquals(1, g.quantidade());
    }

    @Test
    void clienteComConexaoCaidaEhRemovidoSemAtrapalharOsOutros() {
        GerenciadorClientes g = new GerenciadorClientes();
        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso quebrado = new ClienteFalso("Quebrado", true);
        ClienteFalso bia = new ClienteFalso("Bia");
        g.registrar(ana);
        // "registrar" já envia OK, então um cliente que falha nunca chega a ser incluído;
        // para simular uma queda depois de conectado, incluímos um que falha só a partir do broadcast.
        ClienteFalso cairaDepois = new ClienteFalso("Cairá") {
            private boolean primeiro = true;

            @Override
            public synchronized boolean enviar(String texto) {
                if (primeiro) {
                    primeiro = false;
                    return super.enviar(texto);
                }
                return false;
            }
        };
        g.registrar(cairaDepois);
        g.registrar(bia);
        assertFalse(g.registrar(quebrado));

        assertEquals(2, g.broadcast("teste"));
        assertEquals(2, g.quantidade());
        assertTrue(cairaDepois.fechado());
        assertTrue(bia.recebidas().contains("teste"));
        assertTrue(ana.recebidas().contains("teste"));
    }

    @Test
    void broadcastsSimultaneosNaoPerdemNemDuplicamMensagens() throws InterruptedException {
        final int remetentes = 8;
        final int mensagensPorRemetente = 200;
        GerenciadorClientes g = new GerenciadorClientes();
        List<ClienteFalso> clientes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ClienteFalso c = new ClienteFalso("Cliente" + i);
            clientes.add(c);
            g.registrar(c);
        }

        List<Thread> threads = new ArrayList<>();
        for (int r = 0; r < remetentes; r++) {
            final int numero = r;
            Thread t = new Thread(() -> {
                for (int m = 0; m < mensagensPorRemetente; m++) {
                    g.broadcast("r" + numero + "-m" + m);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        int esperado = remetentes * mensagensPorRemetente + 1; // +1 do "OK" do registro
        List<String> primeira = clientes.get(0).recebidas();
        for (ClienteFalso c : clientes) {
            assertEquals(esperado, c.recebidas().size());
            assertEquals(esperado, c.recebidas().stream().distinct().count());
            // todos recebem exatamente na mesma ordem
            assertEquals(primeira, c.recebidas());
        }
    }

    @Test
    void registrosSimultaneosDoMesmoNomeDeixamApenasUm() throws InterruptedException {
        GerenciadorClientes g = new GerenciadorClientes();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> g.registrar(new ClienteFalso("Ana")));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        assertEquals(1, g.quantidade());
    }

    @Test
    void desconectarTodosFechaEEsvaziaALista() {
        GerenciadorClientes g = new GerenciadorClientes();
        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        g.registrar(ana);
        g.registrar(bia);

        g.desconectarTodos();

        assertEquals(0, g.quantidade());
        assertTrue(ana.fechado());
        assertTrue(bia.fechado());
    }
}
