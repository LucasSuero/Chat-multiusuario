package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.ModeloConversas;
import java.util.function.Consumer;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Cabeçalho (nome e imagem), aviso de conexão, mensagens e campo de envio da conversa aberta. */
final class PainelConversa extends VBox {

    private final StackPane slotAvatar = new StackPane();
    private final Label titulo = new Label();
    private final Label subtitulo = new Label();
    private final Button botaoParticipantes = new Button();
    private final Button botaoAdicionar = new Button();
    private final HBox banner = new HBox(12);
    private final Label textoBanner = new Label();
    private final Button botaoReconectar = new Button("Tentar agora");
    private final MensagensView mensagens = new MensagensView();
    private final TextArea campo = new TextArea();
    private final Button botaoEnviar = new Button();
    private final SimpleBooleanProperty offline = new SimpleBooleanProperty(false);

    private Consumer<String> aoEnviar = texto -> { };

    PainelConversa() {
        getStyleClass().add("painel-conversa");

        titulo.getStyleClass().add("titulo-conversa");
        subtitulo.getStyleClass().add("subtitulo-conversa");
        VBox textos = new VBox(2, titulo, subtitulo);
        textos.setAlignment(Pos.CENTER_LEFT);
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);

        botaoParticipantes.setGraphic(Icones.grupo());
        botaoParticipantes.getStyleClass().add("botao-icone");
        botaoParticipantes.setTooltip(new Tooltip("Ver participantes"));
        botaoAdicionar.setGraphic(Icones.mais());
        botaoAdicionar.getStyleClass().add("botao-icone");
        botaoAdicionar.setTooltip(new Tooltip("Adicionar participante"));
        for (Button b : new Button[] {botaoParticipantes, botaoAdicionar}) {
            b.managedProperty().bind(b.visibleProperty());
        }

        HBox cabecalho = new HBox(14, slotAvatar, textos, espaco, botaoParticipantes, botaoAdicionar);
        cabecalho.setAlignment(Pos.CENTER_LEFT);
        cabecalho.getStyleClass().add("cabecalho-conversa");

        banner.getStyleClass().add("banner-offline");
        banner.setAlignment(Pos.CENTER_LEFT);
        Region espacoBanner = new Region();
        HBox.setHgrow(espacoBanner, Priority.ALWAYS);
        banner.getChildren().addAll(textoBanner, espacoBanner, botaoReconectar);
        banner.setVisible(false);
        banner.managedProperty().bind(banner.visibleProperty());

        campo.setPromptText("Escreva uma mensagem...  (Enter envia, Shift+Enter quebra a linha)");
        campo.setWrapText(true);
        campo.setPrefRowCount(1);
        campo.setPrefHeight(46);
        campo.setMinHeight(46);
        campo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                enviar();
            }
        });
        campo.textProperty().addListener((o, antes, texto) -> {
            int linhas = Math.max(1, Math.min(5, texto.split("\n", -1).length));
            campo.setPrefHeight(26 + linhas * 20);
        });
        campo.disableProperty().bind(offline);
        HBox.setHgrow(campo, Priority.ALWAYS);

        botaoEnviar.setGraphic(Icones.enviar());
        botaoEnviar.getStyleClass().add("botao-enviar");
        botaoEnviar.setTooltip(new Tooltip("Enviar mensagem"));
        botaoEnviar.setOnAction(e -> enviar());
        botaoEnviar.disableProperty().bind(campo.textProperty().isEmpty().or(offline));

        HBox entrada = new HBox(12, campo, botaoEnviar);
        entrada.setAlignment(Pos.BOTTOM_CENTER);
        entrada.getStyleClass().add("barra-entrada");
        entrada.setPadding(new Insets(14, 20, 14, 20));

        VBox.setVgrow(mensagens, Priority.ALWAYS);
        getChildren().addAll(cabecalho, banner, mensagens, entrada);
    }

    MensagensView mensagens() {
        return mensagens;
    }

    void aoEnviar(Consumer<String> acao) {
        this.aoEnviar = acao;
    }

    void aoVerParticipantes(Runnable acao) {
        botaoParticipantes.setOnAction(e -> acao.run());
    }

    void aoAdicionar(Runnable acao) {
        botaoAdicionar.setOnAction(e -> acao.run());
    }

    void aoTentarAgora(Runnable acao) {
        botaoReconectar.setOnAction(e -> acao.run());
    }

    void mostrar(ModeloConversas.Item item, long meuId) {
        slotAvatar.getChildren().setAll(new AvatarView(item.imagem(), 42, item.ehGrupo()));
        titulo.setText(item.titulo());
        subtitulo.setText(item.ehGeral() ? "Sala geral · todos os usuários conectados recebem"
                : item.ehGrupo() ? "Grupo" : "Conversa particular");
        botaoParticipantes.setVisible(item.ehGrupo());
        botaoAdicionar.setVisible(item.ehGrupo() && !item.ehGeral()); // todos já estão na sala Geral
        mensagens.configurar(meuId);
        campo.clear();
        campo.requestFocus();
    }

    void definirSubtitulo(String texto) {
        subtitulo.setText(texto);
    }

    /** Liga/desliga o aviso de conexão perdida e bloqueia o envio enquanto estiver offline. */
    void definirOffline(boolean ligado, String texto, boolean mostrarBotao) {
        offline.set(ligado);
        banner.setVisible(ligado);
        textoBanner.setText(texto);
        botaoReconectar.setVisible(mostrarBotao);
        botaoReconectar.setManaged(mostrarBotao);
    }

    private void enviar() {
        String texto = campo.getText() == null ? "" : campo.getText().strip();
        if (texto.isEmpty() || offline.get()) {
            return;
        }
        aoEnviar.accept(texto);
        campo.clear();
        mensagens.rolarParaFim();
    }
}
