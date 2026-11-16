package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.server.ProcessadorComandos;
import br.unicesumar.chat.server.RegistroSessoes;
import br.unicesumar.chat.server.Roteador;
import br.unicesumar.chat.server.Servidor;
import br.unicesumar.chat.service.ServicoChat;
import br.unicesumar.chat.testsupport.RepositoriosFalsos;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Clientes de terminal conversando pelo servidor real (repositórios em memória), via entrada/saída simuladas. */
class ClienteConsoleTest {

    /** Um console com teclado (pipe) e tela (buffer) para o teste digitar e ler. */
    private final class Console {
        final PipedOutputStream teclado = new PipedOutputStream();
        final ByteArrayOutputStream tela = new ByteArrayOutputStream();
        final AtomicInteger codigo = new AtomicInteger(-1);
        final Thread thread;

        Console(String usuario, boolean novo) throws IOException {
            PipedInputStream entrada = new PipedInputStream(teclado);
            PrintStream saida = new PrintStream(tela, true, StandardCharsets.UTF_8);
            ClienteConsole cliente = new ClienteConsole(new ClienteChat(), saida, ZoneId.of("UTC"));
            BufferedReader leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            thread = new Thread(() -> {
                try {
                    codigo.set(cliente.executar("localhost", servidor.porta(), usuario, "segredo1", novo, leitor));
                } catch (IOException | InterruptedException e) {
                    codigo.set(99);
                }
            });
            thread.start();
            consoles.add(this);
        }

        void digitar(String linha) throws IOException {
            teclado.write((linha + "\n").getBytes(StandardCharsets.UTF_8));
            teclado.flush();
        }

        String texto() {
            return tela.toString(StandardCharsets.UTF_8);
        }

        void esperarTexto(String trecho) throws InterruptedException {
            for (int i = 0; i < 100 && !texto().contains(trecho); i++) {
                Thread.sleep(50);
            }
            assertTrue(texto().contains(trecho), "esperava na tela: \"" + trecho + "\" mas a tela tem:\n" + texto());
        }
    }

    private Servidor servidor;
    private final List<Console> consoles = new ArrayList<>();

    @BeforeEach
    void iniciar() throws IOException {
        RepositoriosFalsos repos = new RepositoriosFalsos();
        ServicoChat servico = new ServicoChat(repos.usuarios, repos.conversas, repos.mensagens, Clock.systemUTC());
        RegistroSessoes registro = new RegistroSessoes();
        servidor = new Servidor(0, new ProcessadorComandos(servico, new Roteador(servico, registro)), registro);
        servidor.iniciar();
    }

    @AfterEach
    void encerrar() throws Exception {
        for (Console c : consoles) {
            try {
                c.digitar("/sair");
            } catch (IOException e) {
                // já encerrado
            }
            c.thread.join(3000);
        }
        servidor.parar();
    }

    @Test
    void tresConsolesTrocamMensagensNaSalaGeralComHistoricoParaQuemEntraDepois() throws Exception {
        Console ana = new Console("ana", true);
        ana.esperarTexto("conectado como ana");
        Console bruno = new Console("bruno", true);
        bruno.esperarTexto("conectado como bruno");

        ana.digitar("olá, pessoal");
        bruno.esperarTexto("ana: olá, pessoal");
        ana.esperarTexto("ana: olá, pessoal");
        bruno.digitar("oi, ana");
        ana.esperarTexto("bruno: oi, ana");

        Console carla = new Console("carla", true); // entra depois: o histórico vem sozinho
        carla.esperarTexto("conectado como carla");
        carla.esperarTexto("ana: olá, pessoal");
        carla.esperarTexto("bruno: oi, ana");
        assertTrue(carla.texto().indexOf("ana: olá, pessoal") < carla.texto().indexOf("--- fim do histórico"),
                "o histórico aparece antes do aviso de fim");
        carla.digitar("cheguei");
        ana.esperarTexto("carla: cheguei");
        bruno.esperarTexto("carla: cheguei");
    }

    @Test
    void loginInexistenteMostraFalhaEEncerraComCodigoDeErro() throws Exception {
        Console fantasma = new Console("fantasma", false);
        fantasma.thread.join(8000);
        assertEquals(1, fantasma.codigo.get());
        assertTrue(fantasma.texto().contains("Falha ao entrar"));
    }

    @Test
    void sairEncerraComCodigoZero() throws Exception {
        Console ana = new Console("ana", true);
        ana.esperarTexto("conectado como ana");
        ana.digitar("/sair");
        ana.thread.join(5000);
        assertEquals(0, ana.codigo.get());
    }
}
