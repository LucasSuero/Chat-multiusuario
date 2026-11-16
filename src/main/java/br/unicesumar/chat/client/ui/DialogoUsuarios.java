package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.Avatares;
import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.model.Usuario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Busca usuários no servidor (BUSCAR_USUARIOS) e age sobre o escolhido: abrir conversa particular,
 * montar um grupo (vários usuários, nome e imagem) ou adicionar alguém a um grupo existente.
 */
final class DialogoUsuarios {

    enum Modo { PRIVADA, GRUPO, ADICIONAR }

    interface Acao {
        void confirmar(String nomeGrupo, String imagemGrupo, List<Usuario> usuarios);
    }

    private final ClienteChat cliente;
    private final Modo modo;
    private final Acao acao;
    private final Set<Long> ocultos;
    private final Dialog<Void> dialogo;

    private final TextField campoBusca = new TextField();
    private final ListView<Usuario> resultados = new ListView<>();
    private final Label dica = new Label("Digite parte do nome para buscar.");
    private final Map<Long, Usuario> escolhidos = new LinkedHashMap<>();
    private final FlowPane chips = new FlowPane(6, 6);
    private final TextField campoNome = new TextField();
    private final AvatarSeletor imagem = new AvatarSeletor(Avatares.idsGrupos(), true, Avatares.PADRAO_GRUPO, 40);
    private final Button botaoConfirmar = new Button("Criar grupo");
    private final PauseTransition atraso = new PauseTransition(Duration.millis(300));

    DialogoUsuarios(ClienteChat cliente, ControleApp controle, Modo modo, Set<Long> ocultos, Acao acao) {
        this.cliente = cliente;
        this.modo = modo;
        this.acao = acao;
        this.ocultos = ocultos;

        String textoTitulo = switch (modo) {
            case PRIVADA -> "Nova conversa particular";
            case GRUPO -> "Criar grupo";
            case ADICIONAR -> "Adicionar participante";
        };
        Label titulo = new Label(textoTitulo);
        titulo.getStyleClass().add("titulo-dialogo");

        VBox conteudo = new VBox(14, titulo);
        conteudo.setPadding(new Insets(8, 4, 4, 4));

        if (modo == Modo.GRUPO) {
            campoNome.setPromptText("Nome do grupo");
            campoNome.textProperty().addListener((o, antes, depois) -> atualizarEstado());
            conteudo.getChildren().addAll(rotulado("NOME DO GRUPO", campoNome), rotulado("IMAGEM DO GRUPO", imagem));
        }

        campoBusca.setPromptText("Buscar usuários pelo nome...");
        conteudo.getChildren().add(rotulado(modo == Modo.GRUPO ? "ADICIONAR INTEGRANTES" : "USUÁRIO", campoBusca));

        if (modo == Modo.GRUPO) {
            chips.setVisible(false);
            chips.managedProperty().bind(chips.visibleProperty());
            conteudo.getChildren().add(chips);
        }

        dica.getStyleClass().add("texto-suave");
        dica.setWrapText(true);
        resultados.getStyleClass().add("lista-usuarios");
        resultados.setPlaceholder(dica);
        resultados.setPrefHeight(modo == Modo.GRUPO ? 170 : 240);
        resultados.setCellFactory(lv -> new ListCell<Usuario>() {
            {
                setPrefWidth(0);
            }

            @Override
            protected void updateItem(Usuario u, boolean vazio) {
                super.updateItem(u, vazio);
                setText(null);
                if (vazio || u == null) {
                    setGraphic(null);
                    return;
                }
                Label nome = new Label(u.username());
                nome.getStyleClass().add("titulo-item");
                HBox linha = new HBox(12, new AvatarView(u.avatar(), 36, false), nome);
                linha.setAlignment(Pos.CENTER_LEFT);
                linha.getStyleClass().add("item-usuario");
                setGraphic(linha);
            }
        });
        resultados.setOnMouseClicked(e -> {
            Usuario u = resultados.getSelectionModel().getSelectedItem();
            resultados.getSelectionModel().clearSelection();
            if (u != null) {
                escolher(u);
            }
        });
        conteudo.getChildren().add(resultados);

        if (modo == Modo.GRUPO) {
            botaoConfirmar.getStyleClass().add("botao-primario");
            botaoConfirmar.setMaxWidth(Double.MAX_VALUE);
            botaoConfirmar.setOnAction(e -> confirmarGrupo());
            conteudo.getChildren().add(botaoConfirmar);
            atualizarEstado();
        }

        atraso.setOnFinished(e -> {
            String termo = campoBusca.getText().strip();
            if (!termo.isEmpty()) {
                cliente.buscarUsuarios(termo);
            }
        });
        campoBusca.textProperty().addListener((o, antes, depois) -> {
            atraso.stop();
            if (depois == null || depois.isBlank()) {
                resultados.getItems().clear();
                dica.setText("Digite parte do nome para buscar.");
            } else {
                atraso.playFromStart();
            }
        });

        dialogo = Dialogos.criar(controle, textoTitulo, conteudo, 440);
    }

    void mostrar() {
        dialogo.show();
        Platform.runLater(campoBusca::requestFocus);
    }

    void aoFechar(Runnable acaoAoFechar) {
        dialogo.setOnHidden(e -> acaoAoFechar.run());
    }

    /** Resultados da busca vindos do servidor. Resultados de um termo já substituído são ignorados. */
    void aoResultados(String termo, List<Usuario> lista) {
        if (!termo.equals(campoBusca.getText().strip())) {
            return;
        }
        List<Usuario> visiveis = new ArrayList<>();
        for (Usuario u : lista) {
            if (!ocultos.contains(u.id())) {
                visiveis.add(u);
            }
        }
        resultados.getItems().setAll(visiveis);
        dica.setText("Nenhum usuário encontrado para \"" + termo + "\".");
    }

    private void escolher(Usuario u) {
        if (modo == Modo.GRUPO) {
            escolhidos.put(u.id(), u);
            atualizarChips();
        } else {
            acao.confirmar(null, null, List.of(u));
            dialogo.close();
        }
    }

    private void confirmarGrupo() {
        acao.confirmar(campoNome.getText().strip(), imagem.selecionado(), new ArrayList<>(escolhidos.values()));
        dialogo.close();
    }

    private void atualizarChips() {
        chips.getChildren().clear();
        for (Usuario u : escolhidos.values()) {
            Label nome = new Label(u.username());
            Button remover = new Button();
            remover.setGraphic(Icones.fechar());
            remover.getStyleClass().add("botao-icone");
            remover.setStyle("-fx-padding: 0;");
            remover.setOnAction(e -> {
                escolhidos.remove(u.id());
                atualizarChips();
            });
            HBox chip = new HBox(4, nome, remover);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("chip");
            chips.getChildren().add(chip);
        }
        chips.setVisible(!escolhidos.isEmpty());
        atualizarEstado();
    }

    private void atualizarEstado() {
        boolean nomeOk = !campoNome.getText().isBlank();
        botaoConfirmar.setDisable(!(nomeOk && !escolhidos.isEmpty()));
    }

    private static VBox rotulado(String rotulo, javafx.scene.Node no) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("rotulo-campo");
        return new VBox(6, r, no);
    }
}
