package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.ModeloConversas;
import br.unicesumar.chat.client.Tema;
import br.unicesumar.chat.model.Usuario;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Menu lateral: conversas particulares e grupos separados, busca, perfil, tema e configurações. */
final class BarraLateral extends VBox {

    private final ControleApp controle;
    private final TextField busca = new TextField();
    private final ListView<Object> lista = new ListView<>();
    private final StackPane slotAvatar = new StackPane();
    private final Label nomeUsuario = new Label();
    private final Label estado = new Label();
    private final Button botaoTema = new Button();

    private ModeloConversas modelo = new ModeloConversas();
    private long selecionada;
    private boolean atualizando;

    private Consumer<Long> aoSelecionar = id -> { };
    private Runnable aoNovaPrivada = () -> { };
    private Runnable aoNovoGrupo = () -> { };
    private Runnable aoConfiguracoes = () -> { };

    BarraLateral(ControleApp controle) {
        this.controle = controle;
        getStyleClass().add("barra-lateral");
        setMinWidth(240);
        setPrefWidth(310);
        setMaxWidth(340);

        Label titulo = new Label("Conversas");
        titulo.getStyleClass().add("titulo-app");
        Region espaco = new Region();
        HBox.setHgrow(espaco, Priority.ALWAYS);
        Button botaoNova = new Button();
        botaoNova.setGraphic(Icones.mais());
        botaoNova.getStyleClass().add("botao-icone");
        botaoNova.setTooltip(new Tooltip("Nova conversa ou grupo"));
        ContextMenu menu = new ContextMenu();
        MenuItem privada = new MenuItem("Nova conversa particular");
        privada.setOnAction(e -> aoNovaPrivada.run());
        MenuItem grupo = new MenuItem("Criar grupo");
        grupo.setOnAction(e -> aoNovoGrupo.run());
        menu.getItems().addAll(privada, grupo);
        botaoNova.setOnAction(e -> menu.show(botaoNova, javafx.geometry.Side.BOTTOM, 0, 4));

        HBox topo = new HBox(10, Icones.marca(26, true), titulo, espaco, botaoNova);
        topo.setAlignment(Pos.CENTER_LEFT);
        topo.getStyleClass().add("topo-lateral");

        busca.setPromptText("Buscar conversas...");
        busca.getStyleClass().add("campo-busca");
        busca.textProperty().addListener((o, antes, depois) -> reconstruir());
        VBox blocoBusca = new VBox(busca);
        blocoBusca.setPadding(new Insets(0, 14, 6, 14));

        Label vazio = new Label("Nenhuma conversa ainda.\nUse o botão + para começar.");
        vazio.getStyleClass().add("texto-suave");
        vazio.setWrapText(true);
        lista.setPlaceholder(vazio);
        lista.getStyleClass().add("lista-conversas");
        lista.setCellFactory(lv -> new ListCell<Object>() {
            {
                setPrefWidth(0);
            }

            @Override
            protected void updateItem(Object item, boolean vazioCelula) {
                super.updateItem(item, vazioCelula);
                setText(null);
                if (vazioCelula || item == null) {
                    setGraphic(null);
                    setMouseTransparent(false);
                } else if (item instanceof String secao) {
                    Label l = new Label(secao);
                    l.getStyleClass().add("secao-titulo");
                    setGraphic(l);
                    setMouseTransparent(true);
                } else {
                    setMouseTransparent(false);
                    setGraphic(criarItem((ModeloConversas.Item) item));
                }
            }
        });
        lista.getSelectionModel().selectedItemProperty().addListener((o, antes, item) -> {
            if (!atualizando && item instanceof ModeloConversas.Item i) {
                aoSelecionar.accept(i.id());
            }
        });
        VBox.setVgrow(lista, Priority.ALWAYS);

        nomeUsuario.getStyleClass().add("nome-usuario");
        estado.getStyleClass().add("estado-conexao");
        VBox dados = new VBox(1, nomeUsuario, estado);
        dados.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dados, Priority.ALWAYS);
        botaoTema.getStyleClass().add("botao-icone");
        botaoTema.setTooltip(new Tooltip("Alternar tema claro/escuro"));
        botaoTema.setOnAction(e -> controle.alternarTema());
        Button botaoConfig = new Button();
        botaoConfig.setGraphic(Icones.ajustes());
        botaoConfig.getStyleClass().add("botao-icone");
        botaoConfig.setTooltip(new Tooltip("Perfil e configurações"));
        botaoConfig.setOnAction(e -> aoConfiguracoes.run());
        HBox painelUsuario = new HBox(10, slotAvatar, dados, botaoTema, botaoConfig);
        painelUsuario.setAlignment(Pos.CENTER_LEFT);
        painelUsuario.getStyleClass().add("painel-usuario");
        VBox rodape = new VBox(painelUsuario);
        rodape.setPadding(new Insets(10, 12, 12, 12));

        getChildren().addAll(topo, blocoBusca, lista, rodape);
        atualizarIconeTema();
    }

    void aoSelecionar(Consumer<Long> acao) {
        this.aoSelecionar = acao;
    }

    void aoNovaPrivada(Runnable acao) {
        this.aoNovaPrivada = acao;
    }

    void aoNovoGrupo(Runnable acao) {
        this.aoNovoGrupo = acao;
    }

    void aoConfiguracoes(Runnable acao) {
        this.aoConfiguracoes = acao;
    }

    void definirUsuario(Usuario u) {
        slotAvatar.getChildren().setAll(new AvatarView(u.avatar(), 38, false));
        nomeUsuario.setText(u.username());
    }

    void definirEstado(String texto, String classe) {
        estado.setText(texto);
        estado.getStyleClass().setAll(classe);
    }

    void atualizarIconeTema() {
        botaoTema.setGraphic(controle.tema() == Tema.ESCURO ? Icones.sol() : Icones.lua());
    }

    void atualizar(ModeloConversas modelo, long selecionadaId) {
        this.modelo = modelo;
        this.selecionada = selecionadaId;
        reconstruir();
    }

    private void reconstruir() {
        String filtro = busca.getText() == null ? "" : busca.getText().strip().toLowerCase();
        List<Object> itens = new ArrayList<>();
        List<ModeloConversas.Item> privadas = filtrar(modelo.privadas(), filtro);
        List<ModeloConversas.Item> grupos = filtrar(modelo.grupos(), filtro);
        if (!privadas.isEmpty()) {
            itens.add("CONVERSAS PARTICULARES");
            itens.addAll(privadas);
        }
        if (!grupos.isEmpty()) {
            itens.add("GRUPOS");
            itens.addAll(grupos);
        }
        atualizando = true;
        lista.getItems().setAll(itens);
        for (int i = 0; i < itens.size(); i++) {
            if (itens.get(i) instanceof ModeloConversas.Item item && item.id() == selecionada) {
                lista.getSelectionModel().select(i);
            }
        }
        atualizando = false;
    }

    private static List<ModeloConversas.Item> filtrar(List<ModeloConversas.Item> itens, String filtro) {
        if (filtro.isEmpty()) {
            return itens;
        }
        List<ModeloConversas.Item> r = new ArrayList<>();
        for (ModeloConversas.Item i : itens) {
            if (i.titulo().toLowerCase().contains(filtro)) {
                r.add(i);
            }
        }
        return r;
    }

    private Node criarItem(ModeloConversas.Item item) {
        Label titulo = new Label(item.titulo());
        titulo.getStyleClass().add("titulo-item");
        String textoPrevia = item.previa().isEmpty() ? (item.ehGrupo() ? "Grupo" : "Conversa particular") : item.previa();
        Label previa = new Label(textoPrevia);
        previa.getStyleClass().add("previa-item");

        HBox linhaTitulo = new HBox(6, titulo);
        linhaTitulo.setAlignment(Pos.CENTER_LEFT);
        if (item.naoLidas() > 0) {
            Label badge = new Label(item.naoLidas() > 99 ? "99+" : String.valueOf(item.naoLidas()));
            badge.getStyleClass().add("badge");
            Region espaco = new Region();
            HBox.setHgrow(espaco, Priority.ALWAYS);
            linhaTitulo.getChildren().addAll(espaco, badge);
        }
        VBox textos = new VBox(2, linhaTitulo, previa);
        textos.setMinWidth(0);
        HBox.setHgrow(textos, Priority.ALWAYS);

        HBox linha = new HBox(12, new AvatarView(item.imagem(), 40, item.ehGrupo()), textos);
        linha.setAlignment(Pos.CENTER_LEFT);
        linha.getStyleClass().add("item-conversa");
        return linha;
    }
}
