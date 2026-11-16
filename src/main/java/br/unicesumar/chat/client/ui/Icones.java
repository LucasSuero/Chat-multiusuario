package br.unicesumar.chat.client.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * Ícones desenhados com formas simples (sem arquivos de imagem nem fontes de ícones), todos no mesmo
 * estilo: traço arredondado de 1,8 px ou preenchimento sólido. A cor vem do tema via CSS.
 */
public final class Icones {

    private static final double TAM = 20;

    private Icones() {
    }

    private static <T extends Shape> T linha(T forma) {
        forma.getStyleClass().add("icone-linha");
        return forma;
    }

    private static <T extends Shape> T solido(T forma) {
        forma.getStyleClass().add("icone-solido");
        return forma;
    }

    private static StackPane caixa(double tamanho, String classe, Node... nos) {
        StackPane p = new StackPane(new Group(nos));
        p.getStyleClass().add(classe);
        p.setMinSize(tamanho, tamanho);
        p.setPrefSize(tamanho, tamanho);
        p.setMaxSize(tamanho, tamanho);
        return p;
    }

    private static StackPane caixa(Node... nos) {
        return caixa(TAM, "icone", nos);
    }

    public static Node enviar() {
        return caixa(solido(new Polygon(2, 3, 18.5, 10, 2, 17, 5.5, 10)));
    }

    public static Node mais() {
        return caixa(linha(new Line(10, 4, 10, 16)), linha(new Line(4, 10, 16, 10)));
    }

    public static Node busca() {
        return caixa(linha(new Circle(8.5, 8.5, 5.2)), linha(new Line(12.4, 12.4, 16.5, 16.5)));
    }

    public static Node ajustes() {
        return caixa(
                linha(new Line(3, 5, 17, 5)), linha(new Line(3, 10, 17, 10)), linha(new Line(3, 15, 17, 15)),
                solido(new Circle(7, 5, 2.3)), solido(new Circle(13, 10, 2.3)), solido(new Circle(8, 15, 2.3)));
    }

    public static Node pessoa() {
        Rectangle corpo = solido(new Rectangle(3.5, 11.5, 13, 6.5));
        corpo.setArcWidth(8);
        corpo.setArcHeight(8);
        return caixa(solido(new Circle(10, 6.5, 3.6)), corpo);
    }

    public static Node grupo() {
        Rectangle corpo1 = solido(new Rectangle(1.5, 11.5, 11, 6));
        corpo1.setArcWidth(6);
        corpo1.setArcHeight(6);
        Rectangle corpo2 = solido(new Rectangle(10.5, 11.5, 8, 5.5));
        corpo2.setArcWidth(6);
        corpo2.setArcHeight(6);
        return caixa(solido(new Circle(7, 6.5, 3.1)), corpo1, solido(new Circle(14.3, 7, 2.6)), corpo2);
    }

    public static Node sol() {
        Node[] nos = new Node[9];
        nos[0] = linha(new Circle(10, 10, 3.6));
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            nos[i + 1] = linha(new Line(10 + Math.cos(a) * 6.3, 10 + Math.sin(a) * 6.3,
                    10 + Math.cos(a) * 8.6, 10 + Math.sin(a) * 8.6));
        }
        return caixa(nos);
    }

    public static Node lua() {
        Shape crescente = Shape.subtract(new Circle(10, 10, 7), new Circle(14, 7, 6));
        return caixa(solido(crescente));
    }

    public static Node sair() {
        return caixa(
                linha(new Line(8, 3, 3, 3)), linha(new Line(3, 3, 3, 17)), linha(new Line(3, 17, 8, 17)),
                linha(new Line(8, 10, 16, 10)), solido(new Polygon(13.5, 6.5, 18, 10, 13.5, 13.5)));
    }

    public static Node fechar() {
        return caixa(linha(new Line(5, 5, 15, 15)), linha(new Line(15, 5, 5, 15)));
    }

    public static Node check() {
        return caixa(linha(new Line(4, 10.5, 8, 14.5)), linha(new Line(8, 14.5, 16, 6)));
    }

    /** Balão de conversa, usado como logotipo. primario=true usa a cor de destaque do tema. */
    public static Node marca(double tamanho, boolean primario) {
        double k = tamanho / 24.0;
        Rectangle balao = solido(new Rectangle(2 * k, 3 * k, 20 * k, 14 * k));
        balao.setArcWidth(8 * k);
        balao.setArcHeight(8 * k);
        Polygon cauda = solido(new Polygon(6 * k, 15 * k, 6 * k, 22 * k, 13 * k, 16 * k));
        return caixa(tamanho, primario ? "icone-primario" : "icone-marca", balao, cauda);
    }
}
