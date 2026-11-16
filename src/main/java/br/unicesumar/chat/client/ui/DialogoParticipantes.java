package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.model.Usuario;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Lista os participantes de um grupo (dados vindos do servidor) e permite adicionar mais gente. */
final class DialogoParticipantes {

    private final Dialog<Void> dialogo;
    private final ListView<Usuario> lista = new ListView<>();
    private final Label contagem = new Label("Carregando...");
    private final Set<Long> ids = new HashSet<>();

    DialogoParticipantes(ControleApp controle, String nomeGrupo, long meuId, boolean podeAdicionar, Runnable aoAdicionar) {
        Label titulo = new Label(nomeGrupo);
        titulo.getStyleClass().add("titulo-dialogo");
        contagem.getStyleClass().add("texto-suave");

        lista.getStyleClass().add("lista-usuarios");
        lista.setPrefHeight(280);
        lista.setCellFactory(lv -> new ListCell<Usuario>() {
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
                Label nome = new Label(u.id() == meuId ? u.username() + " (você)" : u.username());
                nome.getStyleClass().add("titulo-item");
                HBox linha = new HBox(12, new AvatarView(u.avatar(), 36, false), nome);
                linha.setAlignment(Pos.CENTER_LEFT);
                linha.getStyleClass().add("item-usuario");
                setGraphic(linha);
            }
        });

        VBox conteudo = new VBox(12, titulo, contagem, lista);
        conteudo.setPadding(new Insets(8, 4, 4, 4));
        if (podeAdicionar) {
            Button adicionar = new Button("Adicionar participante");
            adicionar.getStyleClass().add("botao-primario");
            adicionar.setMaxWidth(Double.MAX_VALUE);
            adicionar.setOnAction(e -> aoAdicionar.run());
            conteudo.getChildren().add(adicionar);
        }
        dialogo = Dialogos.criar(controle, "Participantes", conteudo, 420);
    }

    void definir(List<Usuario> participantes) {
        ids.clear();
        for (Usuario u : participantes) {
            ids.add(u.id());
        }
        lista.getItems().setAll(participantes);
        int n = participantes.size();
        contagem.setText(n + (n == 1 ? " participante" : " participantes"));
    }

    Set<Long> ids() {
        return ids;
    }

    void mostrar() {
        dialogo.show();
    }

    void aoFechar(Runnable acao) {
        dialogo.setOnHidden(e -> acao.run());
    }
}
