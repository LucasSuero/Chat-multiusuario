package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.service.ChatException;
import br.unicesumar.chat.service.Erro;
import br.unicesumar.chat.service.ServicoChat;
import br.unicesumar.chat.testsupport.RepositoriosFalsos;
import br.unicesumar.chat.testsupport.SessaoFalsa;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Encaminhamento de mensagens e isolamento entre conversas, sem rede e sem banco. */
class RoteadorTest {

    RepositoriosFalsos repos;
    ServicoChat servico;
    RegistroSessoes registro;
    Roteador roteador;
    Usuario ana;
    Usuario bruno;
    Usuario carla;
    SessaoFalsa sAna;
    SessaoFalsa sBruno;
    SessaoFalsa sCarla;

    @BeforeEach
    void preparar() {
        repos = new RepositoriosFalsos();
        servico = new ServicoChat(repos.usuarios, repos.conversas, repos.mensagens, Clock.systemUTC());
        registro = new RegistroSessoes();
        roteador = new Roteador(servico, registro);
        ana = repos.usuarios.criar("ana", "h", "avatar01");
        bruno = repos.usuarios.criar("bruno", "h", "avatar02");
        carla = repos.usuarios.criar("carla", "h", "avatar03");
        sAna = conectar(ana);
        sBruno = conectar(bruno);
        sCarla = conectar(carla);
    }

    private SessaoFalsa conectar(Usuario u) {
        SessaoFalsa s = new SessaoFalsa(u);
        registro.adicionar(s);
        return s;
    }

    @Test
    void mensagemChegaSomenteAosParticipantes() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        roteador.enviar(ana, conversa, "oi bruno");
        assertEquals(List.of("oi bruno"), sBruno.conteudos());
        assertTrue(sCarla.recebidas().isEmpty(), "quem não participa não pode receber nada");
    }

    @Test
    void remetenteTambemRecebeAMensagemComoConfirmacao() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        roteador.enviar(ana, conversa, "oi");
        assertEquals(List.of("oi"), sAna.conteudos());
    }

    @Test
    void conversasDiferentesSaoIsoladas() {
        long anaBruno = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        long anaCarla = servico.abrirPrivada(ana, carla.id()).conversa().id();
        roteador.enviar(ana, anaBruno, "para o bruno");
        roteador.enviar(ana, anaCarla, "para a carla");
        assertEquals(List.of("para o bruno"), sBruno.conteudos());
        assertEquals(List.of("para a carla"), sCarla.conteudos());
        assertEquals(List.of("para o bruno", "para a carla"), sAna.conteudos());
    }

    @Test
    void grupoEntregaATodosOsIntegrantesESoAeles() {
        Usuario diego = repos.usuarios.criar("diego", "h", "avatar04");
        SessaoFalsa sDiego = conectar(diego);
        long grupo = servico.criarGrupo(ana, "Grupo", null, List.of(bruno.id(), carla.id())).conversa().id();
        roteador.enviar(bruno, grupo, "olá grupo");
        assertEquals(List.of("olá grupo"), sAna.conteudos());
        assertEquals(List.of("olá grupo"), sBruno.conteudos());
        assertEquals(List.of("olá grupo"), sCarla.conteudos());
        assertTrue(sDiego.recebidas().isEmpty());
    }

    @Test
    void todasAsSessoesDoMesmoUsuarioRecebem() {
        SessaoFalsa outroDispositivo = conectar(bruno);
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        roteador.enviar(ana, conversa, "oi");
        assertEquals(1, sBruno.conteudos().size());
        assertEquals(1, outroDispositivo.conteudos().size());
    }

    @Test
    void sessaoSemLoginNaoRecebeNada() {
        SessaoFalsa anonima = new SessaoFalsa();
        registro.adicionar(anonima);
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        roteador.enviar(ana, conversa, "oi");
        assertTrue(anonima.recebidas().isEmpty());
    }

    @Test
    void participanteOfflineNaoImpedeEntregaEAMensagemFicaSalva() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        registro.remover(sBruno);
        roteador.enviar(ana, conversa, "você viu isso?");
        assertEquals(List.of("você viu isso?"), sAna.conteudos());
        assertTrue(sBruno.recebidas().isEmpty());
        List<Mensagem> historico = servico.entrarNaConversa(bruno, conversa);
        assertEquals("você viu isso?", historico.get(0).conteudo());
    }

    @Test
    void naoParticipanteNaoConsegueEnviarNemGeraEntregaOuRegistro() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        ChatException e = assertThrows(ChatException.class, () -> roteador.enviar(carla, conversa, "intruso"));
        assertEquals(Erro.CONVERSA_INACESSIVEL, e.erro());
        assertTrue(sAna.recebidas().isEmpty());
        assertTrue(sBruno.recebidas().isEmpty());
        assertEquals(0, repos.mensagens.total());
    }

    @Test
    void sessaoQueSaiuDoRegistroDeixaDeReceber() {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        roteador.enviar(ana, conversa, "primeira");
        registro.remover(sBruno);
        roteador.enviar(ana, conversa, "segunda");
        assertEquals(List.of("primeira"), sBruno.conteudos());
        assertEquals(List.of("primeira", "segunda"), sAna.conteudos());
    }

    @Test
    void envioConcorrenteMantemMesmaOrdemParaTodosOsParticipantes() throws Exception {
        long conversa = servico.abrirPrivada(ana, bruno.id()).conversa().id();
        int porRemetente = 200;
        List<Thread> threads = new ArrayList<>();
        for (Usuario remetente : List.of(ana, bruno)) {
            Thread t = new Thread(() -> {
                for (int i = 0; i < porRemetente; i++) {
                    roteador.enviar(remetente, conversa, remetente.username() + "-" + i);
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        List<String> vistoPorAna = sAna.recebidas("MSG");
        List<String> vistoPorBruno = sBruno.recebidas("MSG");
        assertEquals(2 * porRemetente, vistoPorAna.size(), "nenhuma mensagem pode se perder");
        assertEquals(vistoPorAna, vistoPorBruno, "todos devem ver a mesma sequência");
        long anterior = 0;
        for (String linha : vistoPorAna) {
            long id = Long.parseLong(linha.split("\t")[1]);
            assertTrue(id > anterior, "ids devem crescer na ordem de entrega");
            anterior = id;
        }
        assertEquals(2 * porRemetente, repos.mensagens.total());
    }
}
