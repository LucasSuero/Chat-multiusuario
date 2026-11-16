package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.Preferencias;
import br.unicesumar.chat.client.Tema;
import javafx.stage.Stage;

/** O que as telas precisam da aplicação: tema, preferências, janela e saída da conta. */
public interface ControleApp {

    Tema tema();

    void definirTema(Tema tema);

    default void alternarTema() {
        definirTema(tema().alternar());
    }

    /** Volta para a tela de login, encerrando a sessão atual. */
    void aoSairDaConta();

    Stage janela();

    Preferencias preferencias();
}
