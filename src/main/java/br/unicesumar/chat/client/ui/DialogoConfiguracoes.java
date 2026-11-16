package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.Avatares;
import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.client.Tema;
import br.unicesumar.chat.model.Usuario;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Perfil (trocar avatar, gravado no servidor), aparência (tema) e saída da conta. */
final class DialogoConfiguracoes {

    private final ClienteChat cliente;
    private final Dialog<Void> dialogo;
    private final StackPane slotAvatar = new StackPane();
    private final Label nome = new Label();
    private final Label status = new Label();
    private final AvatarSeletor seletor;

    DialogoConfiguracoes(ClienteChat cliente, ControleApp controle, Usuario eu, String servidor) {
        this.cliente = cliente;

        Label titulo = new Label("Configurações");
        titulo.getStyleClass().add("titulo-dialogo");

        nome.getStyleClass().add("nome-usuario");
        Label sub = new Label("Conectado a " + servidor);
        sub.getStyleClass().add("texto-suave");
        VBox dadosPerfil = new VBox(2, nome, sub);
        dadosPerfil.setAlignment(Pos.CENTER_LEFT);
        HBox perfil = new HBox(16, slotAvatar, dadosPerfil);
        perfil.setAlignment(Pos.CENTER_LEFT);

        seletor = new AvatarSeletor(Avatares.idsUsuarios(), false, eu.avatar(), 42);
        Button salvar = new Button("Salvar avatar");
        salvar.getStyleClass().add("botao-primario");
        salvar.setOnAction(e -> {
            status.setText("");
            cliente.alterarAvatar(seletor.selecionado());
        });
        status.getStyleClass().add("mensagem-sucesso");
        HBox acoesAvatar = new HBox(14, salvar, status);
        acoesAvatar.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup grupo = new ToggleGroup();
        ToggleButton claro = new ToggleButton("Claro");
        ToggleButton escuro = new ToggleButton("Escuro");
        for (ToggleButton t : new ToggleButton[] {claro, escuro}) {
            t.setToggleGroup(grupo);
            t.getStyleClass().add("seletor-tema");
        }
        (controle.tema() == Tema.ESCURO ? escuro : claro).setSelected(true);
        grupo.selectedToggleProperty().addListener((o, antes, agora) -> {
            if (agora == null) {
                antes.setSelected(true);
                return;
            }
            controle.definirTema(agora == escuro ? Tema.ESCURO : Tema.CLARO);
            Dialogos.atualizarEstilos(dialogo(), controle);
        });
        HBox temas = new HBox(10, claro, escuro);

        Button sair = new Button("Sair da conta");
        sair.getStyleClass().add("botao-perigo");
        sair.setGraphic(Icones.sair());
        sair.setOnAction(e -> {
            dialogo().close();
            controle.aoSairDaConta();
        });

        VBox conteudo = new VBox(16, titulo, perfil,
                secao("SEU AVATAR", seletor), acoesAvatar,
                secao("APARÊNCIA", temas),
                sair);
        conteudo.setPadding(new Insets(8, 4, 4, 4));

        dialogo = Dialogos.criar(controle, "Configurações", conteudo, 480);
        mostrarPerfil(eu);
    }

    private Dialog<Void> dialogo() {
        return dialogo;
    }

    /** Chamado quando o servidor confirma a troca de avatar. */
    void perfilAtualizado(Usuario u) {
        mostrarPerfil(u);
        status.setText("Avatar atualizado");
    }

    private void mostrarPerfil(Usuario u) {
        slotAvatar.getChildren().setAll(new AvatarView(u.avatar(), 64, false));
        nome.setText(u.username());
    }

    void mostrar() {
        dialogo.show();
    }

    void aoFechar(Runnable acao) {
        dialogo.setOnHidden(e -> acao.run());
    }

    private static VBox secao(String rotulo, javafx.scene.Node conteudo) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("rotulo-campo");
        return new VBox(8, r, conteudo);
    }
}
