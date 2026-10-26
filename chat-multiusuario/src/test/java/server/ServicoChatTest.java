package server;

import model.Mensagem;
import model.Protocolo;
import persistence.RepositorioException;
import persistence.RepositorioMensagens;
import org.junit.jupiter.api.Test;
import server.GerenciadorClientesTest.ClienteFalso;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras de gravação e histórico, sem rede e sem banco: tudo com repositório e clientes falsos. */
class ServicoChatTest {

    /** Repositório em memória que imita o contrato do repositório JDBC. */
    static class RepositorioFalso implements RepositorioMensagens {
        private final List<Mensagem> guardadas = new ArrayList<>();
        boolean falhaAoSalvar;
        boolean falhaAoBuscar;
        int ultimaQuantidadePedida = -1;

        @Override
        public synchronized void salvar(Mensagem mensagem) {
            if (falhaAoSalvar) {
                throw new RepositorioException("banco fora do ar", new SQLException("caiu"));
            }
            guardadas.add(mensagem);
        }

        @Override
        public synchronized List<Mensagem> buscarUltimas(int quantidade) {
            ultimaQuantidadePedida = quantidade;
            if (falhaAoBuscar) {
                throw new RepositorioException("banco fora do ar", new SQLException("caiu"));
            }
            int inicio = Math.max(0, guardadas.size() - quantidade);
            return new ArrayList<>(guardadas.subList(inicio, guardadas.size()));
        }

        synchronized List<Mensagem> guardadas() {
            return new ArrayList<>(guardadas);
        }
    }

    private final RepositorioFalso repositorio = new RepositorioFalso();
    private final List<String> avisosDoLog = new ArrayList<>();
    private final ServicoChat servico =
            new ServicoChat(new GerenciadorClientes(), repositorio, avisosDoLog::add);

    private static Mensagem antiga(String remetente, String texto, int minuto) {
        return new Mensagem(remetente, texto, LocalDateTime.of(2026, 10, 20, 9, minuto));
    }

    @Test
    void mensagemEhGravadaComRemetenteConteudoEHorarioEEnviadaATodos() {
        ClienteFalso ana = new ClienteFalso("Ana");
        ClienteFalso bia = new ClienteFalso("Bia");
        servico.entrar(ana);
        servico.entrar(bia);

        servico.enviarMensagem("Ana", "  bom dia  ");

        List<Mensagem> gravadas = repositorio.guardadas();
        assertEquals(1, gravadas.size());
        assertEquals("Ana", gravadas.get(0).getRemetente());
        assertEquals("bom dia", gravadas.get(0).getConteudo());
        assertTrue(gravadas.get(0).getHorario() != null);
        assertTrue(ana.recebidas().contains(gravadas.get(0).formatar()));
        assertTrue(bia.recebidas().contains(gravadas.get(0).formatar()));
    }

    @Test
    void mensagemInvalidaNaoEhGravadaNemTransmitida() {
        ClienteFalso ana = new ClienteFalso("Ana");
        servico.entrar(ana);

        String grande = "a".repeat(Mensagem.TAMANHO_MAXIMO + 1);
        assertThrows(IllegalArgumentException.class, () -> servico.enviarMensagem("Ana", grande));

        assertTrue(repositorio.guardadas().isEmpty());
        assertEquals(List.of(Protocolo.OK), ana.recebidas());
    }

    @Test
    void falhaAoGravarNaoImpedeOBroadcastEVaiParaOLog() {
        ClienteFalso ana = new ClienteFalso("Ana");
        servico.entrar(ana);
        repositorio.falhaAoSalvar = true;

        servico.enviarMensagem("Ana", "ainda funciona");

        assertEquals(2, ana.recebidas().size());
        assertTrue(ana.recebidas().get(1).endsWith("Ana: ainda funciona"));
        assertEquals(1, avisosDoLog.size());
    }

    @Test
    void clienteNovoRecebeOkDepoisOHistoricoNaOrdemCerta() {
        Mensagem m1 = antiga("Bia", "primeira", 1);
        Mensagem m2 = antiga("Caio", "segunda", 2);
        repositorio.salvar(m1);
        repositorio.salvar(m2);
        ClienteFalso ana = new ClienteFalso("Ana");

        assertTrue(servico.entrar(ana));

        assertEquals(List.of(Protocolo.OK, ServicoChat.INICIO_HISTORICO,
                m1.formatar(), m2.formatar(), ServicoChat.FIM_HISTORICO), ana.recebidas());
    }

    @Test
    void historicoPedeAQuantidadePadraoDeMensagens() {
        servico.entrar(new ClienteFalso("Ana"));
        assertEquals(ServicoChat.TAMANHO_HISTORICO, repositorio.ultimaQuantidadePedida);
    }

    @Test
    void semHistoricoOClienteRecebeApenasOOk() {
        ClienteFalso ana = new ClienteFalso("Ana");
        servico.entrar(ana);
        assertEquals(List.of(Protocolo.OK), ana.recebidas());
    }

    @Test
    void falhaAoLerOHistoricoNaoImpedeAEntrada() {
        repositorio.falhaAoBuscar = true;
        ClienteFalso ana = new ClienteFalso("Ana");

        assertTrue(servico.entrar(ana));

        assertEquals(1, servico.quantidade());
        assertEquals(List.of(Protocolo.OK), ana.recebidas());
        assertEquals(1, avisosDoLog.size());
    }

    @Test
    void nomeRepetidoEhRecusadoESemRecebimentoDeHistorico() {
        repositorio.salvar(antiga("Bia", "oi", 1));
        servico.entrar(new ClienteFalso("Ana"));
        ClienteFalso repetida = new ClienteFalso("ANA");

        assertFalse(servico.entrar(repetida));

        assertTrue(repetida.recebidas().isEmpty());
        assertEquals(1, servico.quantidade());
    }

    @Test
    void avisosDoSistemaChegamAosClientesMasNaoSaoGravados() {
        ClienteFalso ana = new ClienteFalso("Ana");
        servico.entrar(ana);

        servico.anunciar("Bia entrou no chat.");

        assertTrue(repositorio.guardadas().isEmpty());
        assertTrue(ana.recebidas().get(1).endsWith("Sistema: Bia entrou no chat."));
    }

    @Test
    void ordemGravadaEhIgualAOrdemDeEntregaComEnviosSimultaneos() throws InterruptedException {
        ClienteFalso ouvinte = new ClienteFalso("Ouvinte");
        servico.entrar(ouvinte);

        List<Thread> threads = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            final String nome = "Remetente" + r;
            Thread t = new Thread(() -> {
                for (int m = 0; m < 100; m++) {
                    servico.enviarMensagem(nome, "msg-" + m);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        List<String> gravadas = repositorio.guardadas().stream().map(Mensagem::formatar).toList();
        List<String> entregues = ouvinte.recebidas().subList(1, ouvinte.recebidas().size());
        assertEquals(400, gravadas.size());
        assertEquals(gravadas, entregues);
    }

    @Test
    void clienteQueEntraNoMeioDosEnviosNaoPerdeNemRepeteMensagens() throws InterruptedException {
        servico.entrar(new ClienteFalso("Fixo"));
        Thread remetente = new Thread(() -> {
            for (int m = 0; m < 300; m++) {
                servico.enviarMensagem("Fixo", "msg-" + m);
            }
        });
        remetente.start();
        while (repositorio.guardadas().size() < 100) {
            Thread.sleep(1);
        }
        ClienteFalso novo = new ClienteFalso("Novo");
        servico.entrar(novo);
        remetente.join();

        // Tira o OK e as marcas do histórico: sobra o histórico seguido das mensagens ao vivo.
        List<String> vistas = new ArrayList<>(novo.recebidas());
        vistas.remove(Protocolo.OK);
        vistas.remove(ServicoChat.INICIO_HISTORICO);
        vistas.remove(ServicoChat.FIM_HISTORICO);

        List<String> gravadas = repositorio.guardadas().stream().map(Mensagem::formatar).toList();
        // As mensagens vistas têm de ser exatamente o final da lista gravada: sem buracos e sem repetição.
        assertEquals(gravadas.subList(gravadas.size() - vistas.size(), gravadas.size()), vistas);
        assertTrue(vistas.size() >= ServicoChat.TAMANHO_HISTORICO);
    }
}
