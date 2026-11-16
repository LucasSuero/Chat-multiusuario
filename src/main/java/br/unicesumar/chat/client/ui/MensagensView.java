package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.AgrupadorMensagens;
import br.unicesumar.chat.model.Mensagem;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Área de mensagens: balões à direita (enviadas) e à esquerda (recebidas), com avatar e nome em todas,
 * separadores de dia e rolagem automática para o fim quando o usuário já está embaixo.
 */
final class MensagensView extends ScrollPane {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final VBox lista = new VBox(4);
    private final ZoneId zona = ZoneId.systemDefault();
    private long meuId;
    private Mensagem ultima;
    private boolean seguirFim = true;

    MensagensView() {
        getStyleClass().add("area-mensagens");
        lista.getStyleClass().add("lista-mensagens");
        setContent(lista);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        vvalueProperty().addListener((o, antes, v) -> seguirFim = v.doubleValue() >= 0.97);
        lista.heightProperty().addListener((o, antes, h) -> {
            if (seguirFim) {
                setVvalue(1.0);
            }
        });
    }

    void configurar(long meuId) {
        this.meuId = meuId;
    }

    void limpar() {
        lista.getChildren().clear();
        ultima = null;
        seguirFim = true;
    }

    /** Id da última mensagem exibida (0 se vazio): serve para detectar mensagens fora de ordem. */
    long ultimoId() {
        return ultima == null ? 0 : ultima.id();
    }

    void rolarParaFim() {
        seguirFim = true;
        setVvalue(1.0);
    }

    void substituir(List<Mensagem> mensagens) {
        limpar();
        for (Mensagem m : mensagens) {
            adicionar(m, false);
        }
        rolarParaFim();
    }

    void adicionar(Mensagem m, boolean animar) {
        AgrupadorMensagens.Linha linha = AgrupadorMensagens.calcular(ultima, m, zona, LocalDate.now(zona));
        if (linha.separadorDia() != null) {
            Label separador = new Label(linha.separadorDia());
            separador.getStyleClass().add("separador-dia");
            HBox caixa = new HBox(separador);
            caixa.setAlignment(Pos.CENTER);
            caixa.setPadding(new Insets(12, 0, 8, 0));
            lista.getChildren().add(caixa);
        }
        Node linhaNo = criarLinha(m, linha.inicioBloco());
        lista.getChildren().add(linhaNo);
        ultima = m;
        if (m.enviadaPor(meuId)) {
            seguirFim = true;
        }
        if (animar) {
            linhaNo.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(220), linhaNo);
            fade.setToValue(1);
            TranslateTransition sobe = new TranslateTransition(Duration.millis(220), linhaNo);
            sobe.setFromY(10);
            sobe.setToY(0);
            fade.play();
            sobe.play();
        }
    }

    private Node criarLinha(Mensagem m, boolean inicioBloco) {
        boolean minha = m.enviadaPor(meuId);

        Label nome = new Label(m.remetente().username());
        nome.getStyleClass().add(minha ? "balao-nome-enviado" : "balao-nome");
        Label hora = new Label(HORA.format(m.enviadaEm().atZone(zona)));
        hora.getStyleClass().add(minha ? "balao-hora-enviado" : "balao-hora");
        HBox cabecalho = new HBox(8, new AvatarView(m.remetente().avatar(), 24, false), nome, hora);
        cabecalho.setAlignment(Pos.CENTER_LEFT);

        Label texto = new Label(m.conteudo());
        texto.setWrapText(true);
        texto.getStyleClass().add(minha ? "balao-texto-enviado" : "balao-texto");

        VBox balao = new VBox(5, cabecalho, texto);
        balao.getStyleClass().addAll("balao", minha ? "balao-enviado" : "balao-recebido");
        balao.maxWidthProperty().bind(widthProperty().multiply(0.68));

        HBox linha = new HBox(balao);
        linha.setAlignment(minha ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        linha.setPadding(new Insets(inicioBloco ? 8 : 0, 0, 0, 0));
        return linha;
    }
}
