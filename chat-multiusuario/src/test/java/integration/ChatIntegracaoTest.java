package integration;

import client.ClienteChat;
import model.Mensagem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import persistence.RepositorioException;
import persistence.RepositorioMensagens;
import server.Servidor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de ponta a ponta com sockets reais em localhost (servidor na porta 0 + clientes
 * de verdade) e banco em memória. Não faz parte dos testes unitários: fica de fora do
 * {@code mvn test} comum. Para rodar: {@code mvn test -Dtestes.excluidos=nenhum}.
 */
@Tag("integracao")
class ChatIntegracaoTest {

    static class BancoEmMemoria implements RepositorioMensagens {
        private final List<Mensagem> guardadas = new ArrayList<>();

        @Override
        public synchronized void salvar(Mensagem m) {
            guardadas.add(m);
        }

        @Override
        public synchronized List<Mensagem> buscarUltimas(int n) {
            return new ArrayList<>(guardadas.subList(Math.max(0, guardadas.size() - n), guardadas.size()));
        }
    }

    static class Coletor implements ClienteChat.Ouvinte {
        final List<String> linhas = Collections.synchronizedList(new ArrayList<>());
        volatile boolean desconectado;

        @Override
        public void aoReceberLinha(String linha) {
            linhas.add(linha);
        }

        @Override
        public void aoDesconectar(String motivo) {
            desconectado = true;
        }

        boolean viu(String trecho) {
            synchronized (linhas) {
                return linhas.stream().anyMatch(l -> l.contains(trecho));
            }
        }
    }

    private final Servidor servidor = new Servidor(new BancoEmMemoria(), linha -> { });
    private final List<ClienteChat> abertos = new ArrayList<>();

    @AfterEach
    void encerrar() {
        abertos.forEach(ClienteChat::desconectar);
        servidor.parar();
    }

    private ClienteChat conectar(String nome, Coletor coletor) throws IOException {
        ClienteChat c = new ClienteChat(coletor);
        c.conectar("localhost", servidor.getPorta(), nome);
        abertos.add(c);
        return c;
    }

    private static void esperar(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5_000;
        while (!condicao.getAsBoolean()) {
            assertTrue(System.currentTimeMillis() < limite, "tempo esgotado esperando o resultado");
            Thread.sleep(20);
        }
    }

    @Test
    void tresClientesTrocamMensagensEUmQuartoRecebeOHistorico() throws Exception {
        servidor.iniciar(0);
        Coletor ca = new Coletor(), cb = new Coletor(), cc = new Coletor();
        ClienteChat ana = conectar("Ana", ca);
        ClienteChat bia = conectar("Bia", cb);
        conectar("Caio", cc);

        ana.enviar("oi, pessoal");
        bia.enviar("olá, Ana");

        esperar(() -> ca.viu("Bia: olá, Ana") && cb.viu("Ana: oi, pessoal") && cc.viu("Bia: olá, Ana"));
        assertTrue(cc.viu("Ana: oi, pessoal"));

        Coletor cd = new Coletor();
        conectar("Duda", cd);
        esperar(() -> cd.viu("Fim do histórico"));
        assertTrue(cd.viu("Ana: oi, pessoal"));
        assertTrue(cd.viu("Bia: olá, Ana"));
    }

    @Test
    void desconexaoAbruptaNaoDerrubaOServidorNemOsOutrosClientes() throws Exception {
        servidor.iniciar(0);
        Coletor ca = new Coletor(), cb = new Coletor();
        ClienteChat ana = conectar("Ana", ca);
        ClienteChat bia = conectar("Bia", cb);
        conectar("Caio", new Coletor());
        esperar(() -> servidor.getQuantidadeClientes() == 3);

        ana.desconectar(); // fecha o socket sem aviso

        esperar(() -> servidor.getQuantidadeClientes() == 2);
        bia.enviar("ainda estou aqui");
        esperar(() -> cb.viu("Bia: ainda estou aqui"));
        assertTrue(servidor.estaRodando());
        conectar("Duda", new Coletor()); // o servidor continua aceitando gente nova
    }

    @Test
    void clienteQueParouDeLerEhDesconectadoSemTravarOsOutros() throws Exception {
        servidor.iniciar(0);
        Socket mudo = new Socket("localhost", servidor.getPorta()); // entra e nunca mais lê
        mudo.getOutputStream().write("Mudo\n".getBytes());
        mudo.getOutputStream().flush();
        Coletor ca = new Coletor();
        ClienteChat ana = conectar("Ana", ca);
        esperar(() -> servidor.getQuantidadeClientes() == 2);

        String texto = "x".repeat(480);
        for (int i = 0; i < 12_000 && servidor.getQuantidadeClientes() == 2; i++) {
            ana.enviar(texto);
        }

        esperar(() -> servidor.getQuantidadeClientes() == 1); // o cliente parado foi removido
        ana.enviar("continuo aqui");
        esperar(() -> ca.viu("Ana: continuo aqui")); // e o chat segue funcionando
        mudo.close();
    }

    @Test
    void linhaGigantesEhRecusadaSemDerrubarOServidor() throws Exception {
        servidor.iniciar(0);
        Coletor ca = new Coletor();
        conectar("Ana", ca);
        try (Socket bruto = new Socket("localhost", servidor.getPorta())) {
            OutputStream saida = bruto.getOutputStream();
            saida.write("Bia\n".getBytes());
            saida.write(("y".repeat(300_000) + "\n").getBytes());
            saida.flush();
            esperar(() -> ca.viu("Bia entrou"));
        }
        esperar(() -> servidor.getQuantidadeClientes() == 1);
        assertTrue(servidor.estaRodando());
    }

    @Test
    void nomeEmUsoEhRecusadoComMensagemClara() throws Exception {
        servidor.iniciar(0);
        conectar("Ana", new Coletor());
        IOException erro = assertThrows(IOException.class, () -> conectar("ana", new Coletor()));
        assertTrue(erro.getMessage().contains("já está em uso"));
        assertEquals(1, servidor.getQuantidadeClientes());
    }
}
