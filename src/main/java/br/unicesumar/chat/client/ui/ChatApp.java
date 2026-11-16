package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.client.Preferencias;
import br.unicesumar.chat.client.Tema;
import br.unicesumar.chat.model.Usuario;
import java.io.IOException;
import java.util.Objects;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Ponto de entrada do cliente gráfico. Cuida da janela, do tema e da troca entre a tela de login e a
 * tela principal. Toda a comunicação passa por {@link ClienteChat} (o protocolo real do servidor).
 */
public class ChatApp extends Application implements ControleApp {

    private Preferencias prefs;
    private Tema tema;
    private Stage palco;
    private Scene cena;
    private ClienteChat cliente;
    private LoginView login;
    private MainView principal;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage palcoInicial) {
        palco = palcoInicial;
        prefs = Preferencias.padrao();
        tema = prefs.tema();
        cena = new Scene(new StackPane(), 1120, 720);
        aplicarTema();
        palco.setTitle("Chat Multiusuário");
        palco.setMinWidth(780);
        palco.setMinHeight(540);
        palco.setScene(cena);
        palco.setOnCloseRequest(e -> encerrarTudo());
        mostrarLogin();
        palco.show();
    }

    @Override
    public void stop() {
        encerrarTudo();
    }

    // ------------------------------------------------------------ ControleApp

    @Override
    public Tema tema() {
        return tema;
    }

    @Override
    public void definirTema(Tema novo) {
        tema = novo;
        prefs.definirTema(novo);
        aplicarTema();
        if (login != null) {
            login.temaMudou();
        }
        if (principal != null) {
            principal.temaMudou();
        }
        Parent raiz = cena.getRoot();
        FadeTransition suave = new FadeTransition(Duration.millis(160), raiz);
        suave.setFromValue(0.88);
        suave.setToValue(1);
        suave.play();
    }

    @Override
    public void aoSairDaConta() {
        if (principal != null) {
            principal.encerrar();
            principal = null;
        }
        mostrarLogin();
    }

    @Override
    public Stage janela() {
        return palco;
    }

    @Override
    public Preferencias preferencias() {
        return prefs;
    }

    // ------------------------------------------------------------ telas

    private void aplicarTema() {
        cena.getStylesheets().setAll(css("/css/app.css"), css(tema.recursoCss()));
    }

    private static String css(String recurso) {
        return Objects.requireNonNull(ChatApp.class.getResource(recurso), "Recurso não encontrado: " + recurso)
                .toExternalForm();
    }

    private void mostrarLogin() {
        if (cliente != null) {
            cliente.sair();
        }
        cliente = new ClienteChat();
        principal = null;
        login = new LoginView(prefs, this, this::autenticar);
        cena.setRoot(login);
        login.focar();
    }

    /** Conecta ao servidor e envia LOGIN/REGISTRAR em segundo plano, sem travar a interface. */
    private void autenticar(LoginView.Pedido pedido) {
        LoginView tela = login;
        ClienteChat atual = cliente;
        tela.carregando(true);
        atual.definirOuvinte(new ClienteChat.Ouvinte() {
            @Override
            public void onAutenticado(Usuario usuario) {
                Platform.runLater(() -> aoAutenticar(atual, pedido));
            }

            @Override
            public void onErro(String codigo, String mensagem) {
                Platform.runLater(() -> {
                    tela.carregando(false);
                    tela.mostrarErro(mensagem == null || mensagem.isBlank() ? "Não foi possível entrar" : mensagem);
                });
            }

            @Override
            public void onDesconectado(String motivo) {
                Platform.runLater(() -> {
                    tela.carregando(false);
                    tela.mostrarErro("Conexão perdida: " + motivo);
                });
            }
        });
        Thread t = new Thread(() -> {
            try {
                atual.conectar(pedido.host(), pedido.porta());
                if (pedido.cadastro()) {
                    atual.registrar(pedido.usuario(), pedido.senha(), pedido.avatar());
                } else {
                    atual.login(pedido.usuario(), pedido.senha());
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    tela.carregando(false);
                    tela.mostrarErro("Não foi possível conectar a " + pedido.host() + ":" + pedido.porta()
                            + ". Confira se o servidor está em execução.");
                });
            }
        }, "conexao-login");
        t.setDaemon(true);
        t.start();
    }

    private void aoAutenticar(ClienteChat atual, LoginView.Pedido pedido) {
        if (atual != cliente || principal != null) {
            return;
        }
        prefs.definirServidor(pedido.host(), pedido.porta());
        prefs.definirUltimoUsuario(pedido.usuario());
        MainView tela = new MainView(atual, this);
        principal = tela;
        login = null;
        atual.definirOuvinte(tela.ouvinte());
        cena.setRoot(tela);
        FadeTransition entrada = new FadeTransition(Duration.millis(300), tela);
        entrada.setFromValue(0);
        entrada.setToValue(1);
        entrada.play();
        tela.iniciar();
    }

    private void encerrarTudo() {
        if (principal != null) {
            principal.encerrar();
        }
        if (cliente != null) {
            cliente.sair();
        }
        Platform.exit();
    }
}
