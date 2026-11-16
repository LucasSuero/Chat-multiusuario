package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.client.ModeloConversas;
import br.unicesumar.chat.model.ConversaVisao;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Tela principal: barra lateral + conversa aberta. Não tem lógica de rede: tudo o que mostra vem do
 * {@link ClienteChat} (servidor real) e tudo o que o usuário faz volta para ele. Os eventos chegam na
 * thread da rede e são repassados à thread do JavaFX com Platform.runLater.
 */
public final class MainView extends StackPane {

    private final ClienteChat cliente;
    private final ControleApp controle;
    private final ModeloConversas modelo = new ModeloConversas();
    private final BarraLateral barra;
    private final PainelConversa painel = new PainelConversa();
    private final Node estadoVazio;
    private final Label aviso = new Label();
    private final Object travaReconexao = new Object();
    private final AtomicBoolean encerrada = new AtomicBoolean();
    private final AtomicBoolean reconectando = new AtomicBoolean();

    private long conversaAberta;
    private boolean historicoPendente;
    private boolean selecionarProxima;
    private boolean offline;
    private volatile boolean tentarAgora;
    private Animation animacaoAviso;
    private DialogoUsuarios dialogoUsuarios;
    private DialogoParticipantes dialogoParticipantes;
    private long dialogoParticipantesId;
    private DialogoConfiguracoes dialogoConfiguracoes;

    public MainView(ClienteChat cliente, ControleApp controle) {
        this.cliente = cliente;
        this.controle = controle;
        this.barra = new BarraLateral(controle);
        this.estadoVazio = criarEstadoVazio();

        painel.setVisible(false);
        StackPane areaCentral = new StackPane(estadoVazio, painel);
        BorderPane raiz = new BorderPane();
        raiz.setLeft(barra);
        raiz.setCenter(areaCentral);

        aviso.getStyleClass().add("aviso");
        aviso.setVisible(false);
        aviso.setMouseTransparent(true);
        getChildren().addAll(raiz, aviso);
        StackPane.setAlignment(aviso, Pos.BOTTOM_CENTER);
        StackPane.setMargin(aviso, new Insets(0, 0, 96, 0));

        barra.aoSelecionar(this::abrir);
        barra.aoNovaPrivada(this::abrirNovaPrivada);
        barra.aoNovoGrupo(this::abrirNovoGrupo);
        barra.aoConfiguracoes(this::abrirConfiguracoes);
        painel.aoEnviar(texto -> {
            if (conversaAberta != 0) {
                cliente.enviarMensagem(conversaAberta, texto);
            }
        });
        painel.aoVerParticipantes(this::abrirParticipantes);
        painel.aoAdicionar(this::abrirAdicionar);
        painel.aoTentarAgora(() -> {
            synchronized (travaReconexao) {
                tentarAgora = true;
                travaReconexao.notifyAll();
            }
        });

        widthProperty().addListener((o, antes, largura) ->
                barra.setPrefWidth(Math.max(250, Math.min(340, largura.doubleValue() * 0.27))));

        cliente.usuario().ifPresent(barra::definirUsuario);
        barra.definirEstado("Conectado", "estado-conexao-ok");
    }

    /** Ouvinte que recebe os eventos do ClienteChat e os leva para a thread do JavaFX. */
    public ClienteChat.Ouvinte ouvinte() {
        return ouvinteFx;
    }

    /** Pede ao servidor a lista de conversas do usuário (chamado logo depois do login). */
    public void iniciar() {
        cliente.carregarConversas();
    }

    /** Para a reconexão automática; chamado ao sair da tela. */
    public void encerrar() {
        encerrada.set(true);
        synchronized (travaReconexao) {
            travaReconexao.notifyAll();
        }
    }

    public void temaMudou() {
        barra.atualizarIconeTema();
    }

    // ------------------------------------------------------------ eventos do servidor

    private final ClienteChat.Ouvinte ouvinteFx = new ClienteChat.Ouvinte() {
        @Override
        public void onAutenticado(Usuario u) {
            Platform.runLater(() -> aoReautenticar(u));
        }

        @Override
        public void onPerfilAtualizado(Usuario u) {
            Platform.runLater(() -> aoPerfil(u));
        }

        @Override
        public void onConversa(ConversaVisao c, boolean nova) {
            Platform.runLater(() -> aoConversa(c, nova));
        }

        @Override
        public void onConversasCarregadas(List<ConversaVisao> lista) {
            Platform.runLater(() -> aoConversas(lista));
        }

        @Override
        public void onHistoricoCarregado(long conversaId) {
            Platform.runLater(() -> aoHistorico(conversaId));
        }

        @Override
        public void onMensagem(Mensagem m) {
            Platform.runLater(() -> aoMensagem(m));
        }

        @Override
        public void onUsuariosEncontrados(String termo, List<Usuario> usuarios) {
            Platform.runLater(() -> {
                if (dialogoUsuarios != null) {
                    dialogoUsuarios.aoResultados(termo, usuarios);
                }
            });
        }

        @Override
        public void onParticipantes(long conversaId, List<Usuario> participantes) {
            Platform.runLater(() -> aoParticipantes(conversaId, participantes));
        }

        @Override
        public void onOk(String detalhe) {
            Platform.runLater(() -> {
                if (detalhe != null && !detalhe.isBlank()) {
                    mostrarAviso(detalhe, false);
                }
            });
        }

        @Override
        public void onErro(String codigo, String mensagem) {
            Platform.runLater(() -> aoErro(codigo, mensagem));
        }

        @Override
        public void onDesconectado(String motivo) {
            Platform.runLater(() -> aoDesconectar());
        }
    };

    private void aoReautenticar(Usuario u) {
        barra.definirUsuario(u);
        barra.definirEstado("Conectado", "estado-conexao-ok");
        if (offline) {
            offline = false;
            painel.definirOffline(false, "", false);
            mostrarAviso("Reconectado ao servidor", false);
            cliente.carregarConversas();
            if (conversaAberta != 0) {
                historicoPendente = true;
                cliente.entrarNaConversa(conversaAberta);
            }
        }
    }

    private void aoPerfil(Usuario u) {
        barra.definirUsuario(u);
        if (dialogoConfiguracoes != null) {
            dialogoConfiguracoes.perfilAtualizado(u);
        } else {
            mostrarAviso("Avatar atualizado", false);
        }
    }

    private void aoConversas(List<ConversaVisao> lista) {
        modelo.atualizarTodas(lista);
        // Mensagens que já chegaram (histórico enviado no login) viram a prévia da lista, sem contar como não lidas.
        long meu = meuId();
        for (ConversaVisao v : lista) {
            List<Mensagem> cache = cliente.mensagens(v.conversa().id());
            if (!cache.isEmpty()) {
                modelo.mensagemRecebida(cache.get(cache.size() - 1), true, meu);
            }
        }
        atualizarBarra();
        if (conversaAberta == 0) {
            modelo.geral().ifPresent(geral -> abrir(geral.id())); // abre a sala Geral sozinho
        }
    }

    private void aoConversa(ConversaVisao v, boolean nova) {
        modelo.atualizar(v);
        atualizarBarra();
        if (nova) {
            mostrarAviso("Nova conversa: " + ModeloConversas.titulo(v), false);
        } else if (selecionarProxima) {
            selecionarProxima = false;
            abrir(v.conversa().id());
        }
    }

    private void aoMensagem(Mensagem m) {
        if (modelo.buscar(m.conversaId()).isEmpty()) {
            // Conversa ainda desconhecida: a mensagem já está no cache do ClienteChat; pede a lista ao servidor.
            cliente.carregarConversas();
            return;
        }
        registrarMensagem(m);
    }

    private void registrarMensagem(Mensagem m) {
        boolean aberta = m.conversaId() == conversaAberta;
        modelo.mensagemRecebida(m, aberta, meuId());
        if (aberta) {
            MensagensView exibicao = painel.mensagens();
            if (m.id() < exibicao.ultimoId()) {
                exibicao.substituir(cliente.mensagens(m.conversaId()));
            } else {
                exibicao.adicionar(m, !historicoPendente);
            }
        }
        atualizarBarra();
    }

    private void aoHistorico(long conversaId) {
        if (conversaId == conversaAberta) {
            historicoPendente = false;
            painel.mensagens().rolarParaFim();
        }
    }

    private void aoParticipantes(long conversaId, List<Usuario> participantes) {
        if (conversaId == conversaAberta) {
            int n = participantes.size();
            boolean geral = modelo.buscar(conversaId).map(ModeloConversas.Item::ehGeral).orElse(false);
            painel.definirSubtitulo((geral ? "Sala geral · " : "Grupo · ") + n + (n == 1 ? " participante" : " participantes"));
        }
        if (dialogoParticipantes != null && dialogoParticipantesId == conversaId) {
            dialogoParticipantes.definir(participantes);
        }
    }

    private void aoErro(String codigo, String mensagem) {
        if ("SEM_CONEXAO".equals(codigo)) {
            return; // o aviso de conexão perdida já está na tela
        }
        if (offline && "CREDENCIAIS_INVALIDAS".equals(codigo)) {
            controle.aoSairDaConta();
            return;
        }
        mostrarAviso(mensagem == null || mensagem.isBlank() ? "Não foi possível concluir a ação" : mensagem, true);
    }

    // ------------------------------------------------------------ conexão perdida

    private void aoDesconectar() {
        if (encerrada.get()) {
            return;
        }
        offline = true;
        painel.definirOffline(true, "Conexão perdida. Tentando reconectar...", true);
        barra.definirEstado("Sem conexão", "estado-conexao-erro");
        iniciarReconexao();
    }

    private void iniciarReconexao() {
        if (!reconectando.compareAndSet(false, true)) {
            return;
        }
        Thread t = new Thread(() -> {
            int tentativa = 0;
            while (!encerrada.get()) {
                long espera = Math.min(15_000L, 1_000L << Math.min(tentativa, 4));
                tentativa++;
                synchronized (travaReconexao) {
                    if (!tentarAgora) {
                        try {
                            travaReconexao.wait(espera);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    tentarAgora = false;
                }
                if (encerrada.get()) {
                    break;
                }
                try {
                    cliente.reconectar();
                    break;
                } catch (IOException e) {
                    Platform.runLater(() -> painel.definirOffline(true,
                            "Servidor indisponível. Tentando novamente...", true));
                } catch (IllegalStateException e) {
                    Platform.runLater(controle::aoSairDaConta);
                    break;
                }
            }
            reconectando.set(false);
        }, "reconexao");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------ ações do usuário

    private void abrir(long id) {
        Optional<ModeloConversas.Item> item = modelo.buscar(id);
        if (item.isEmpty()) {
            return;
        }
        conversaAberta = id;
        historicoPendente = true;
        modelo.marcarLida(id);
        painel.mostrar(item.get(), meuId());
        painel.mensagens().substituir(cliente.mensagens(id));
        painel.setVisible(true);
        estadoVazio.setVisible(false);
        cliente.entrarNaConversa(id);
        if (item.get().ehGrupo()) {
            cliente.participantes(id);
        }
        atualizarBarra();
    }

    private void abrirNovaPrivada() {
        DialogoUsuarios d = new DialogoUsuarios(cliente, controle, DialogoUsuarios.Modo.PRIVADA,
                Set.of(meuId()), (nome, imagem, usuarios) -> {
                    selecionarProxima = true;
                    cliente.abrirPrivada(usuarios.get(0).id());
                });
        mostrarDialogoUsuarios(d);
    }

    private void abrirNovoGrupo() {
        DialogoUsuarios d = new DialogoUsuarios(cliente, controle, DialogoUsuarios.Modo.GRUPO,
                Set.of(meuId()), (nome, imagem, usuarios) -> {
                    selecionarProxima = true;
                    cliente.criarGrupo(nome, imagem, usuarios.stream().map(Usuario::id).toList());
                });
        mostrarDialogoUsuarios(d);
    }

    private void abrirAdicionar() {
        long id = conversaAberta;
        if (id == 0) {
            return;
        }
        Set<Long> ocultos = dialogoParticipantes != null ? dialogoParticipantes.ids() : Set.of(meuId());
        DialogoUsuarios d = new DialogoUsuarios(cliente, controle, DialogoUsuarios.Modo.ADICIONAR,
                ocultos, (nome, imagem, usuarios) -> cliente.adicionarParticipante(id, usuarios.get(0).id()));
        mostrarDialogoUsuarios(d);
    }

    private void mostrarDialogoUsuarios(DialogoUsuarios d) {
        dialogoUsuarios = d;
        d.aoFechar(() -> {
            if (dialogoUsuarios == d) {
                dialogoUsuarios = null;
            }
        });
        d.mostrar();
    }

    private void abrirParticipantes() {
        Optional<ModeloConversas.Item> item = modelo.buscar(conversaAberta);
        if (item.isEmpty()) {
            return;
        }
        DialogoParticipantes d = new DialogoParticipantes(controle, item.get().titulo(), meuId(),
                item.get().ehGrupo(), this::abrirAdicionar);
        dialogoParticipantes = d;
        dialogoParticipantesId = conversaAberta;
        d.aoFechar(() -> {
            if (dialogoParticipantes == d) {
                dialogoParticipantes = null;
            }
        });
        d.mostrar();
        cliente.participantes(conversaAberta);
    }

    private void abrirConfiguracoes() {
        Optional<Usuario> eu = cliente.usuario();
        if (eu.isEmpty()) {
            return;
        }
        DialogoConfiguracoes d = new DialogoConfiguracoes(cliente, controle, eu.get(),
                cliente.host() + ":" + cliente.porta());
        dialogoConfiguracoes = d;
        d.aoFechar(() -> {
            if (dialogoConfiguracoes == d) {
                dialogoConfiguracoes = null;
            }
        });
        d.mostrar();
    }

    // ------------------------------------------------------------ apoio

    private long meuId() {
        return cliente.usuario().map(Usuario::id).orElse(0L);
    }

    private void atualizarBarra() {
        barra.atualizar(modelo, conversaAberta);
    }

    private Node criarEstadoVazio() {
        StackPane circulo = new StackPane(Icones.marca(54, true));
        circulo.getStyleClass().add("circulo-vazio");
        circulo.setMinSize(112, 112);
        circulo.setPrefSize(112, 112);
        circulo.setMaxSize(112, 112);
        Label titulo = new Label("Selecione uma conversa");
        titulo.getStyleClass().add("estado-vazio-titulo");
        Label texto = new Label("Escolha uma conversa na lateral ou comece uma nova com o botão +.");
        texto.getStyleClass().add("estado-vazio-texto");
        texto.setWrapText(true);
        texto.setMaxWidth(340);
        VBox caixa = new VBox(16, circulo, titulo, texto);
        caixa.setAlignment(Pos.CENTER);
        caixa.getStyleClass().add("estado-vazio");
        return caixa;
    }

    private void mostrarAviso(String texto, boolean erro) {
        aviso.setText(texto);
        aviso.getStyleClass().removeAll("aviso-ok", "aviso-erro");
        aviso.getStyleClass().add(erro ? "aviso-erro" : "aviso-ok");
        aviso.setOpacity(0);
        aviso.setVisible(true);
        FadeTransition entra = new FadeTransition(Duration.millis(180), aviso);
        entra.setToValue(1);
        PauseTransition pausa = new PauseTransition(Duration.seconds(3.2));
        FadeTransition sai = new FadeTransition(Duration.millis(350), aviso);
        sai.setToValue(0);
        SequentialTransition sequencia = new SequentialTransition(entra, pausa, sai);
        sequencia.setOnFinished(e -> aviso.setVisible(false));
        if (animacaoAviso != null) {
            animacaoAviso.stop();
        }
        animacaoAviso = sequencia;
        sequencia.play();
    }
}
