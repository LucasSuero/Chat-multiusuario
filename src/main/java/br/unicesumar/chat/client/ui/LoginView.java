package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.Avatares;
import br.unicesumar.chat.client.Preferencias;
import br.unicesumar.chat.client.Tema;
import br.unicesumar.chat.service.Validador;
import br.unicesumar.chat.service.ValidacaoException;
import java.util.function.Consumer;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Tela de entrada: login e cadastro (com escolha de avatar), opções de servidor e alternância de tema. */
public final class LoginView extends StackPane {

    /** Dados digitados, já validados no formato. */
    public record Pedido(String host, int porta, String usuario, String senha, boolean cadastro, String avatar) {
    }

    private final ControleApp controle;
    private final Consumer<Pedido> aoEnviar;
    private boolean cadastro;

    private final Label titulo = new Label();
    private final Label subtitulo = new Label();
    private final TextField campoUsuario = new TextField();
    private final PasswordField campoSenha = new PasswordField();
    private final PasswordField campoConfirmacao = new PasswordField();
    private final AvatarSeletor seletor = new AvatarSeletor(Avatares.idsUsuarios(), false, Avatares.PADRAO_USUARIO, 44);
    private final TextField campoHost = new TextField();
    private final TextField campoPorta = new TextField();
    private final Label erro = new Label();
    private final Button botaoPrincipal = new Button();
    private final Button botaoModo = new Button();
    private final Button botaoServidor = new Button("Configurar servidor");
    private final Button botaoTema = new Button();
    private final VBox blocoCadastro = new VBox(14);
    private final VBox blocoServidor = new VBox(10);
    private final VBox formulario = new VBox(16);
    private final VBox cartao = new VBox(18);
    private VBox painelMarca;

    public LoginView(Preferencias prefs, ControleApp controle, Consumer<Pedido> aoEnviar) {
        this.controle = controle;
        this.aoEnviar = aoEnviar;
        getStyleClass().add("login-raiz");

        campoUsuario.setPromptText("Seu nome de usuário");
        campoUsuario.setText(prefs.ultimoUsuario());
        campoSenha.setPromptText("Sua senha");
        campoConfirmacao.setPromptText("Repita a senha");
        campoHost.setText(prefs.host());
        campoHost.setPromptText("Endereço do servidor");
        campoPorta.setText(String.valueOf(prefs.porta()));
        campoPorta.setPromptText("Porta");

        titulo.getStyleClass().add("titulo-grande");
        subtitulo.getStyleClass().add("subtitulo");
        subtitulo.setWrapText(true);
        erro.getStyleClass().add("mensagem-erro");
        erro.setWrapText(true);
        erro.setVisible(false);
        erro.managedProperty().bind(erro.visibleProperty());

        blocoCadastro.getChildren().addAll(
                campo("CONFIRMAR SENHA", campoConfirmacao),
                campo("ESCOLHA SEU AVATAR", seletor));
        HBox hostPorta = new HBox(10, campoHost, campoPorta);
        HBox.setHgrow(campoHost, Priority.ALWAYS);
        campoPorta.setPrefWidth(90);
        campoPorta.setMaxWidth(90);
        blocoServidor.getChildren().addAll(campo("SERVIDOR", hostPorta));
        blocoServidor.getStyleClass().add("painel-opcoes-servidor");
        blocoServidor.setVisible(false);
        blocoServidor.managedProperty().bind(blocoServidor.visibleProperty());
        botaoServidor.getStyleClass().add("botao-link");
        botaoServidor.setOnAction(e -> blocoServidor.setVisible(!blocoServidor.isVisible()));

        botaoPrincipal.getStyleClass().add("botao-primario");
        botaoPrincipal.setMaxWidth(Double.MAX_VALUE);
        botaoPrincipal.setOnAction(e -> enviar());
        botaoModo.getStyleClass().add("botao-link");
        botaoModo.setOnAction(e -> definirModo(!cadastro));
        campoSenha.setOnAction(e -> enviar());
        campoConfirmacao.setOnAction(e -> enviar());
        campoUsuario.setOnAction(e -> campoSenha.requestFocus());

        formulario.getChildren().addAll(titulo, subtitulo,
                campo("NOME DE USUÁRIO", campoUsuario), campo("SENHA", campoSenha),
                blocoCadastro, erro, botaoPrincipal);
        HBox rodape = new HBox(6, botaoModo);
        rodape.setAlignment(Pos.CENTER);
        HBox servidor = new HBox(botaoServidor);
        servidor.setAlignment(Pos.CENTER);
        cartao.getStyleClass().add("cartao");
        cartao.setPadding(new Insets(34));
        cartao.setPrefWidth(440);
        cartao.setMaxWidth(440);
        cartao.getChildren().addAll(formulario, rodape, servidor, blocoServidor);

        botaoTema.getStyleClass().add("botao-icone");
        botaoTema.setTooltip(new Tooltip("Alternar tema claro/escuro"));
        botaoTema.setOnAction(e -> controle.alternarTema());
        temaMudou();

        StackPane centro = new StackPane(cartao);
        centro.setPadding(new Insets(32));
        ScrollPane rolagem = new ScrollPane(centro);
        rolagem.setFitToWidth(true);
        rolagem.setFitToHeight(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(rolagem, Priority.ALWAYS);

        HBox corpo = new HBox(criarPainelMarca(), rolagem);
        getChildren().addAll(corpo, botaoTema);
        StackPane.setAlignment(botaoTema, Pos.TOP_RIGHT);
        StackPane.setMargin(botaoTema, new Insets(16));

        // Em janelas estreitas o painel de marca some e o formulário ocupa tudo.
        widthProperty().addListener((o, antes, largura) -> {
            boolean largo = largura.doubleValue() >= 860;
            painelMarca.setVisible(largo);
            painelMarca.setManaged(largo);
        });

        definirModo(false);
        FadeTransition entrada = new FadeTransition(Duration.millis(350), corpo);
        entrada.setFromValue(0);
        entrada.setToValue(1);
        entrada.play();
    }

    private VBox criarPainelMarca() {
        Label nome = new Label("Chat Multiusuário");
        nome.getStyleClass().add("marca-titulo");
        Label texto = new Label("Converse em tempo real, em conversas privadas e em grupos.");
        texto.getStyleClass().add("marca-texto");
        texto.setWrapText(true);
        VBox pontos = new VBox(12,
                ponto("Mensagens entregues na hora"),
                ponto("Histórico salvo no servidor"),
                ponto("Tema claro e escuro"));
        VBox v = new VBox(20, Icones.marca(64, false), nome, texto, pontos);
        v.getStyleClass().add("painel-marca");
        v.setAlignment(Pos.CENTER_LEFT);
        v.setMinWidth(420);
        v.setPrefWidth(420);
        v.setMaxWidth(420);
        painelMarca = v;
        return v;
    }

    private static HBox ponto(String texto) {
        Label l = new Label(texto);
        l.getStyleClass().add("marca-ponto");
        HBox h = new HBox(10, Icones.check(), l);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static VBox campo(String rotulo, Node entrada) {
        Label r = new Label(rotulo);
        r.getStyleClass().add("rotulo-campo");
        return new VBox(6, r, entrada);
    }

    private void definirModo(boolean novoCadastro) {
        cadastro = novoCadastro;
        titulo.setText(cadastro ? "Criar conta" : "Bem-vindo de volta");
        subtitulo.setText(cadastro ? "Escolha um nome de usuário, uma senha e seu avatar."
                : "Entre para continuar suas conversas.");
        botaoPrincipal.setText(cadastro ? "Criar conta e entrar" : "Entrar");
        botaoModo.setText(cadastro ? "Já tem conta? Entrar" : "Ainda não tem conta? Cadastre-se");
        blocoCadastro.setVisible(cadastro);
        blocoCadastro.setManaged(cadastro);
        erro.setVisible(false);
        FadeTransition t = new FadeTransition(Duration.millis(220), formulario);
        t.setFromValue(0.2);
        t.setToValue(1);
        t.play();
    }

    private void enviar() {
        erro.setVisible(false);
        try {
            String usuario = Validador.username(campoUsuario.getText());
            String senha = campoSenha.getText();
            if (cadastro) {
                Validador.senha(senha.toCharArray());
                if (!senha.equals(campoConfirmacao.getText())) {
                    throw new ValidacaoException("As senhas não coincidem");
                }
            } else if (senha.isEmpty()) {
                throw new ValidacaoException("Informe a senha");
            }
            String host = campoHost.getText() == null ? "" : campoHost.getText().strip();
            if (host.isEmpty()) {
                throw new ValidacaoException("Informe o endereço do servidor");
            }
            int porta;
            try {
                porta = Integer.parseInt(campoPorta.getText().strip());
            } catch (NumberFormatException e) {
                throw new ValidacaoException("Porta inválida");
            }
            if (porta < 1 || porta > 65535) {
                throw new ValidacaoException("Porta inválida");
            }
            aoEnviar.accept(new Pedido(host, porta, usuario, senha, cadastro, seletor.selecionado()));
        } catch (ValidacaoException e) {
            mostrarErro(e.getMessage());
        }
    }

    /** Mostra uma mensagem de erro sob o formulário, com uma tremida discreta do cartão. */
    public void mostrarErro(String mensagem) {
        erro.setText(mensagem);
        erro.setVisible(true);
        TranslateTransition t = new TranslateTransition(Duration.millis(55), cartao);
        t.setFromX(0);
        t.setByX(9);
        t.setCycleCount(6);
        t.setAutoReverse(true);
        t.setOnFinished(e -> cartao.setTranslateX(0));
        t.play();
    }

    /** Bloqueia a tela enquanto conecta/autentica. */
    public void carregando(boolean ligado) {
        campoUsuario.setDisable(ligado);
        campoSenha.setDisable(ligado);
        campoConfirmacao.setDisable(ligado);
        campoHost.setDisable(ligado);
        campoPorta.setDisable(ligado);
        seletor.setDisable(ligado);
        botaoModo.setDisable(ligado);
        botaoPrincipal.setDisable(ligado);
        if (ligado) {
            ProgressIndicator p = new ProgressIndicator();
            p.setPrefSize(18, 18);
            botaoPrincipal.setGraphic(p);
            botaoPrincipal.setText("Conectando...");
        } else {
            botaoPrincipal.setGraphic(null);
            botaoPrincipal.setText(cadastro ? "Criar conta e entrar" : "Entrar");
        }
    }

    /** Atualiza o ícone do botão de tema (no escuro mostra o sol; no claro, a lua). */
    public void temaMudou() {
        botaoTema.setGraphic(controle.tema() == Tema.ESCURO ? Icones.sol() : Icones.lua());
    }

    public void focar() {
        if (campoUsuario.getText().isBlank()) {
            campoUsuario.requestFocus();
        } else {
            campoSenha.requestFocus();
        }
    }
}
