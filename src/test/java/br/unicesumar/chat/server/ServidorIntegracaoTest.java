package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Protocolo;
import br.unicesumar.chat.service.ServicoChat;
import br.unicesumar.chat.testsupport.RepositoriosFalsos;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integração com sockets reais em localhost (repositórios em memória, sem banco):
 * valida ClienteHandler, Servidor, broadcast e tratamento de desconexões.
 */
class ServidorIntegracaoTest {

    /** Cliente mínimo de teste: fala o protocolo de linhas. */
    static final class ClienteTeste {
        final Socket socket;
        final BufferedReader entrada;
        final BufferedWriter saida;

        ClienteTeste(int porta) throws IOException {
            socket = new Socket("localhost", porta);
            socket.setSoTimeout(5000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        void enviar(String nome, Object... campos) throws IOException {
            enviarBruto(Protocolo.codificar(nome, campos));
        }

        void enviarBruto(String linha) throws IOException {
            saida.write(linha);
            saida.write('\n');
            saida.flush();
        }

        /** Lê até aparecer uma linha do evento pedido; devolve seus campos. Falha (timeout) se não vier. */
        List<String> esperar(String evento) throws IOException {
            String linha;
            while ((linha = entrada.readLine()) != null) {
                List<String> campos = Protocolo.dividir(linha);
                if (campos.get(0).equals(evento)) {
                    return campos;
                }
            }
            throw new IOException("Conexão encerrada antes de chegar " + evento);
        }

        /** Lê e devolve todas as linhas até a do evento final (inclusive). */
        List<List<String>> lerAte(String eventoFinal) throws IOException {
            List<List<String>> lidas = new ArrayList<>();
            while (true) {
                String linha = entrada.readLine();
                if (linha == null) {
                    throw new IOException("Conexão encerrada antes de " + eventoFinal);
                }
                List<String> campos = Protocolo.dividir(linha);
                lidas.add(campos);
                if (campos.get(0).equals(eventoFinal)) {
                    return lidas;
                }
            }
        }

        /** Lê tudo o que chegar durante a janela (para contar linhas, inclusive repetidas). */
        List<List<String>> lerTudoPor(int ms) throws IOException {
            List<List<String>> lidas = new ArrayList<>();
            socket.setSoTimeout(ms);
            try {
                String linha;
                while ((linha = entrada.readLine()) != null) {
                    lidas.add(Protocolo.dividir(linha));
                }
            } catch (SocketTimeoutException e) {
                // fim da janela de leitura
            } finally {
                socket.setSoTimeout(5000);
            }
            return lidas;
        }

        /** Verdadeiro se nada chegar durante o intervalo. */
        boolean nadaChegaPor(int ms) throws IOException {
            socket.setSoTimeout(ms);
            try {
                entrada.readLine(); // chegou linha (ou fim da conexão): então algo aconteceu
                return false;
            } catch (SocketTimeoutException e) {
                return true;
            } finally {
                socket.setSoTimeout(5000);
            }
        }

        boolean conexaoEncerrada() {
            try {
                while (entrada.readLine() != null) {
                    // descarta o que sobrou
                }
                return true;
            } catch (IOException e) {
                return !(e instanceof SocketTimeoutException);
            }
        }

        void fecharAbruptamente() throws IOException {
            socket.setSoLinger(true, 0); // RST em vez de FIN: simula queda de rede / processo morto
            socket.close();
        }

        void fechar() {
            try {
                socket.close();
            } catch (IOException e) {
                // nada a fazer
            }
        }
    }

    ServicoChat servico;
    RegistroSessoes registro;
    ProcessadorComandos processador;
    Servidor servidor;
    final List<ClienteTeste> clientes = new ArrayList<>();

    @BeforeEach
    void iniciar() throws IOException {
        RepositoriosFalsos repos = new RepositoriosFalsos();
        servico = new ServicoChat(repos.usuarios, repos.conversas, repos.mensagens, Clock.systemUTC());
        registro = new RegistroSessoes();
        processador = new ProcessadorComandos(servico, new Roteador(servico, registro));
        servidor = new Servidor(0, processador, registro);
        servidor.iniciar();
    }

    @AfterEach
    void encerrar() {
        clientes.forEach(ClienteTeste::fechar);
        servidor.parar();
    }

    private ClienteTeste conectar() throws IOException {
        ClienteTeste c = new ClienteTeste(servidor.porta());
        clientes.add(c);
        return c;
    }

    private long registrar(ClienteTeste c, String nome) throws IOException {
        c.enviar("REGISTRAR", nome, "segredo1", "avatar01");
        long id = Long.parseLong(c.esperar("AUTENTICADO").get(1));
        c.esperar("HISTORICO_FIM"); // o servidor envia sozinho o histórico da sala Geral
        return id;
    }

    private void esperarTotalDeSessoes(int esperado) throws InterruptedException {
        for (int i = 0; i < 60 && registro.total() != esperado; i++) {
            Thread.sleep(50);
        }
        assertEquals(esperado, registro.total());
    }

    @Test
    void tresClientesTrocamMensagensPrivadasEEmGrupo() throws Exception {
        ClienteTeste ana = conectar();
        ClienteTeste bruno = conectar();
        ClienteTeste carla = conectar();
        registrar(ana, "ana");
        long brunoId = registrar(bruno, "bruno");
        long carlaId = registrar(carla, "carla");

        // conversa privada ana-bruno: a carla não recebe nada
        ana.enviar("ABRIR_PRIVADA", brunoId);
        long privada = Long.parseLong(ana.esperar("CONVERSA").get(1));
        bruno.esperar("CONVERSA_NOVA");
        ana.enviar("MSG", privada, "oi bruno");
        assertEquals("oi bruno", bruno.esperar("MSG").get(7));
        assertEquals("oi bruno", ana.esperar("MSG").get(7));
        assertTrue(carla.nadaChegaPor(300), "a carla não participa da conversa privada");

        // grupo com os três: todos recebem
        ana.enviar("CRIAR_GRUPO", "Turma", "", brunoId + "," + carlaId);
        long grupo = Long.parseLong(ana.esperar("CONVERSA").get(1));
        bruno.esperar("CONVERSA_NOVA");
        carla.esperar("CONVERSA_NOVA");
        carla.enviar("MSG", grupo, "olá a todos");
        assertEquals("olá a todos", ana.esperar("MSG").get(7));
        assertEquals("olá a todos", bruno.esperar("MSG").get(7));
        assertEquals("olá a todos", carla.esperar("MSG").get(7));
    }

    @Test
    void desconexaoAbruptaNaoDerrubaOServidorNemTravaOsOutros() throws Exception {
        ClienteTeste ana = conectar();
        ClienteTeste bruno = conectar();
        ClienteTeste carla = conectar();
        registrar(ana, "ana");
        long brunoId = registrar(bruno, "bruno");
        long carlaId = registrar(carla, "carla");
        ana.enviar("CRIAR_GRUPO", "Turma", "", brunoId + "," + carlaId);
        long grupo = Long.parseLong(ana.esperar("CONVERSA").get(1));
        carla.esperar("CONVERSA_NOVA");
        esperarTotalDeSessoes(3);

        bruno.fecharAbruptamente();
        esperarTotalDeSessoes(2);

        ana.enviar("MSG", grupo, "alguém aí?");
        assertEquals("alguém aí?", ana.esperar("MSG").get(7));
        assertEquals("alguém aí?", carla.esperar("MSG").get(7));

        ClienteTeste diego = conectar();
        registrar(diego, "diego");
        esperarTotalDeSessoes(3);
    }

    @Test
    void historicoChegaAoEntrarNaConversaDepoisDeNovoLogin() throws Exception {
        ClienteTeste ana = conectar();
        ClienteTeste bruno = conectar();
        registrar(ana, "ana");
        long brunoId = registrar(bruno, "bruno");
        ana.enviar("ABRIR_PRIVADA", brunoId);
        long conversa = Long.parseLong(ana.esperar("CONVERSA").get(1));
        for (String texto : List.of("um", "dois", "três")) {
            ana.enviar("MSG", conversa, texto);
            ana.esperar("MSG"); // eco = já foi salva e entregue
        }
        bruno.enviar("SAIR");
        bruno.esperar("OK");
        assertTrue(bruno.conexaoEncerrada());

        ClienteTeste brunoDeNovo = conectar();
        brunoDeNovo.enviar("LOGIN", "bruno", "segredo1");
        brunoDeNovo.esperar("AUTENTICADO");
        brunoDeNovo.esperar("HISTORICO_FIM");
        brunoDeNovo.enviar("LISTAR_CONVERSAS");
        List<List<String>> conversas = brunoDeNovo.lerAte("FIM_CONVERSAS");
        assertEquals(3, conversas.size(), "sala Geral + conversa privada + marcador de fim");
        List<String> privada = conversas.stream().filter(l -> l.get(2).equals("PRIVADA")).findFirst().orElseThrow();
        assertEquals("ana", privada.get(6));
        assertEquals(String.valueOf(conversa), privada.get(1));
        assertTrue(conversas.stream().anyMatch(l -> l.get(0).equals("CONVERSA") && l.get(3).equals("Geral")));

        brunoDeNovo.enviar("ENTRAR", conversa);
        List<List<String>> historico = brunoDeNovo.lerAte("HISTORICO_FIM");
        assertEquals(5, historico.size());
        assertEquals("HISTORICO_INICIO", historico.get(0).get(0));
        assertEquals(List.of("um", "dois", "três"),
                historico.subList(1, 4).stream().map(l -> l.get(7)).toList());
    }

    @Test
    void comandosSemLoginSaoRecusadosEAConexaoContinuaUtil() throws Exception {
        ClienteTeste c = conectar();
        c.enviar("MSG", 1, "oi");
        assertEquals("NAO_AUTENTICADO", c.esperar("ERRO").get(1));
        c.enviarBruto("isto não é um comando");
        assertEquals("COMANDO_INVALIDO", c.esperar("ERRO").get(1));
        registrar(c, "ana");
    }

    @Test
    void linhaGrandeDemaisEncerraAConexaoSemAfetarOsDemais() throws Exception {
        ClienteTeste abusivo = conectar();
        ClienteTeste normal = conectar();
        registrar(normal, "ana");
        abusivo.enviarBruto("MSG\t1\t" + "x".repeat(Protocolo.MAX_LINHA + 100));
        assertTrue(abusivo.conexaoEncerrada());
        normal.enviar("LISTAR_CONVERSAS");
        normal.esperar("FIM_CONVERSAS");
    }

    @Test
    void sairResponderOkEFechaAConexao() throws Exception {
        ClienteTeste c = conectar();
        registrar(c, "ana");
        c.enviar("SAIR");
        c.esperar("OK");
        assertTrue(c.conexaoEncerrada());
        esperarTotalDeSessoes(0);
    }

    @Test
    void servidorLotadoRejeitaNovasConexoes() throws Exception {
        Servidor pequeno = new Servidor(0, processador, new RegistroSessoes(), 1);
        pequeno.iniciar();
        try (Socket primeiro = new Socket("localhost", pequeno.porta());
             Socket segundo = new Socket("localhost", pequeno.porta())) {
            segundo.setSoTimeout(5000);
            BufferedReader r = new BufferedReader(new InputStreamReader(segundo.getInputStream(), StandardCharsets.UTF_8));
            assertTrue(r.readLine().startsWith("ERRO\tSERVIDOR_CHEIO"));
            assertNotNull(primeiro);
        } finally {
            pequeno.parar();
        }
    }

    @Test
    void encerrarOServidorFechaAsConexoesEALiberaAPorta() throws Exception {
        ClienteTeste ana = conectar();
        registrar(ana, "ana");
        int porta = servidor.porta();
        servidor.parar();
        assertTrue(ana.conexaoEncerrada(), "o cliente deve perceber o fim da conexão");
        esperarTotalDeSessoes(0);
        assertThrows(IOException.class, () -> new Socket("localhost", porta).close());
    }

    @Test
    void acentosEEmojisAtravessamOServidorSemCorrupcao() throws Exception {
        ClienteTeste ana = conectar();
        ClienteTeste bruno = conectar();
        registrar(ana, "ana");
        long brunoId = registrar(bruno, "bruno");
        ana.enviar("ABRIR_PRIVADA", brunoId);
        long conversa = Long.parseLong(ana.esperar("CONVERSA").get(1));
        String texto = "olá, coração! ✓ 😀 日本語";
        ana.enviar("MSG", conversa, texto);
        assertEquals(texto, bruno.esperar("MSG").get(7));
    }

    @Test
    void broadcastDaSalaGeralChegaATodosOsDemaisClientesConectados() throws Exception {
        ClienteTeste ana = conectar();
        ClienteTeste bruno = conectar();
        ClienteTeste carla = conectar();
        ClienteTeste semLogin = conectar();
        registrar(ana, "ana");
        registrar(bruno, "bruno");
        registrar(carla, "carla");
        long geral = servico.idGeral();

        ana.enviar("MSG", geral, "olá a todos");
        assertEquals("olá a todos", bruno.esperar("MSG").get(7));
        assertEquals("olá a todos", carla.esperar("MSG").get(7));
        assertEquals("olá a todos", ana.esperar("MSG").get(7));
        assertTrue(semLogin.nadaChegaPor(300), "conexão ainda sem login não recebe mensagens");

        bruno.enviar("MSG", geral, "oi, ana");
        assertEquals("oi, ana", ana.esperar("MSG").get(7));
        assertEquals("oi, ana", carla.esperar("MSG").get(7));
    }

    @Test
    void clienteQueEntraDepoisRecebeSozinhoAsUltimasMensagensDaGeral() throws Exception {
        ClienteTeste ana = conectar();
        registrar(ana, "ana");
        long geral = servico.idGeral();
        for (String texto : List.of("um", "dois", "três")) {
            ana.enviar("MSG", geral, texto);
            ana.esperar("MSG");
        }
        ClienteTeste bruno = conectar();
        bruno.enviar("REGISTRAR", "bruno", "segredo1", "avatar02"); // sem nenhum ENTRAR depois
        List<List<String>> lidas = bruno.lerAte("HISTORICO_FIM");
        assertEquals("AUTENTICADO", lidas.get(0).get(0));
        assertEquals("HISTORICO_INICIO", lidas.get(1).get(0));
        assertEquals(List.of("um", "dois", "três"), lidas.subList(2, 5).stream().map(l -> l.get(7)).toList());
        assertEquals(6, lidas.size());
    }

    @Test
    void cadaMensagemDaGeralChegaUmaSoVezACadaClienteNaMesmaOrdemSobConcorrencia() throws Exception {
        ClienteTeste ana = conectar();
        ClienteTeste bruno = conectar();
        ClienteTeste carla = conectar();
        registrar(ana, "ana");
        registrar(bruno, "bruno");
        registrar(carla, "carla");
        long geral = servico.idGeral();
        int porCliente = 20;
        List<Thread> threads = new ArrayList<>();
        for (ClienteTeste c : List.of(ana, bruno, carla)) {
            Thread t = new Thread(() -> {
                try {
                    for (int i = 0; i < porCliente; i++) {
                        c.enviar("MSG", geral, "m" + System.identityHashCode(c) + "-" + i);
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        List<List<Long>> idsPorCliente = new ArrayList<>();
        for (ClienteTeste c : List.of(ana, bruno, carla)) {
            List<Long> ids = c.lerTudoPor(2000).stream().filter(l -> l.get(0).equals("MSG"))
                    .map(l -> Long.parseLong(l.get(1))).toList();
            assertEquals(3 * porCliente, ids.size(), "cada cliente recebe cada mensagem exatamente uma vez (inclusive a sua)");
            assertEquals(ids.size(), ids.stream().distinct().count(), "sem repetições");
            for (int i = 1; i < ids.size(); i++) {
                assertTrue(ids.get(i) > ids.get(i - 1), "ordem crescente de gravação");
            }
            idsPorCliente.add(ids);
        }
        assertEquals(idsPorCliente.get(0), idsPorCliente.get(1));
        assertEquals(idsPorCliente.get(0), idsPorCliente.get(2));
    }
}
