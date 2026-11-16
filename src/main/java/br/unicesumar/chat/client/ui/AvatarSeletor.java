package br.unicesumar.chat.client.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;

/** Galeria clicável de avatares (ou imagens de grupo); destaca o escolhido. */
public final class AvatarSeletor extends FlowPane {

    private final Map<String, StackPane> opcoes = new LinkedHashMap<>();
    private String selecionado;
    private Consumer<String> aoMudar = id -> { };

    public AvatarSeletor(List<String> ids, boolean grupo, String inicial, double tamanho) {
        super(8, 8);
        for (String id : ids) {
            StackPane opcao = new StackPane(new AvatarView(id, tamanho, grupo));
            opcao.getStyleClass().add("avatar-opcao");
            if (grupo) {
                opcao.getStyleClass().add("avatar-opcao-grupo");
            }
            opcao.setOnMouseClicked(e -> selecionar(id));
            opcoes.put(id, opcao);
            getChildren().add(opcao);
        }
        selecionar(opcoes.containsKey(inicial) ? inicial : ids.get(0));
    }

    public String selecionado() {
        return selecionado;
    }

    public void aoMudar(Consumer<String> acao) {
        this.aoMudar = acao;
    }

    public void selecionar(String id) {
        if (!opcoes.containsKey(id)) {
            return;
        }
        if (selecionado != null) {
            opcoes.get(selecionado).getStyleClass().remove("avatar-opcao-ativo");
        }
        selecionado = id;
        opcoes.get(id).getStyleClass().add("avatar-opcao-ativo");
        aoMudar.accept(id);
    }
}
