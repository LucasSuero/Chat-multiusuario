package br.unicesumar.chat.client.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

/** Criação padronizada dos diálogos: mesma folha de estilo da janela principal e botão "Fechar". */
final class Dialogos {

    private Dialogos() {
    }

    static Dialog<Void> criar(ControleApp controle, String titulo, Node conteudo, double largura) {
        Dialog<Void> dialogo = new Dialog<>();
        dialogo.initOwner(controle.janela());
        dialogo.setTitle(titulo);
        dialogo.setHeaderText(null);
        DialogPane painel = dialogo.getDialogPane();
        painel.getStylesheets().setAll(controle.janela().getScene().getStylesheets());
        painel.setContent(conteudo);
        painel.getButtonTypes().add(new ButtonType("Fechar", ButtonBar.ButtonData.CANCEL_CLOSE));
        painel.setPrefWidth(largura);
        return dialogo;
    }

    /** Reaplica as folhas de estilo da janela (usado depois de trocar o tema com o diálogo aberto). */
    static void atualizarEstilos(Dialog<?> dialogo, ControleApp controle) {
        dialogo.getDialogPane().getStylesheets().setAll(controle.janela().getScene().getStylesheets());
    }
}
