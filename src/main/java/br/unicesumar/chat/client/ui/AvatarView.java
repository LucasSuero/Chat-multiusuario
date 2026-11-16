package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.Avatares;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * Avatar desenhado em código a partir do id da galeria (avatar01.., grupo01..):
 * fundo em degradê + motivo geométrico. Usuários são círculos; grupos, quadrados arredondados.
 */
public final class AvatarView extends StackPane {

    private static final Color BRANCO = Color.color(1, 1, 1, 0.93);

    public AvatarView(String id, double tamanho, boolean grupo) {
        Avatares.Estilo estilo = grupo ? Avatares.grupo(id) : Avatares.usuario(id);
        Shape base;
        if (grupo) {
            Rectangle r = new Rectangle(tamanho, tamanho);
            r.setArcWidth(tamanho * 0.42);
            r.setArcHeight(tamanho * 0.42);
            base = r;
        } else {
            base = new Circle(tamanho / 2);
        }
        base.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web(estilo.cor1())), new Stop(1, Color.web(estilo.cor2()))));
        getChildren().addAll(base, motivo(estilo.motivo(), tamanho * 0.46, tamanho));
        setMinSize(tamanho, tamanho);
        setPrefSize(tamanho, tamanho);
        setMaxSize(tamanho, tamanho);
    }

    private static Node motivo(Avatares.Motivo motivo, double m, double tamanho) {
        switch (motivo) {
            case TRIANGULO: {
                Polygon p = new Polygon(0, -m / 2, m / 2, m / 2, -m / 2, m / 2);
                p.setFill(BRANCO);
                return p;
            }
            case LOSANGO: {
                Polygon p = new Polygon(0, -m / 2, m / 2, 0, 0, m / 2, -m / 2, 0);
                p.setFill(BRANCO);
                return p;
            }
            case QUADRADO: {
                Rectangle r = new Rectangle(m * 0.85, m * 0.85);
                r.setArcWidth(m * 0.25);
                r.setArcHeight(m * 0.25);
                r.setFill(BRANCO);
                return r;
            }
            case ANEL: {
                Circle c = new Circle(m / 2);
                c.setFill(Color.TRANSPARENT);
                c.setStroke(BRANCO);
                c.setStrokeWidth(Math.max(2, tamanho * 0.09));
                return c;
            }
            case CRUZ: {
                Rectangle h = new Rectangle(-m / 2, -m * 0.13, m, m * 0.26);
                Rectangle v = new Rectangle(-m * 0.13, -m / 2, m * 0.26, m);
                h.setFill(BRANCO);
                v.setFill(BRANCO);
                return new Group(h, v);
            }
            default: {
                double w = m * 0.24;
                Rectangle a = new Rectangle(-m * 0.5, -m * 0.1, w, m * 0.6);
                Rectangle b = new Rectangle(-m * 0.12, -m * 0.5, w, m);
                Rectangle c = new Rectangle(m * 0.26, -m * 0.3, w, m * 0.8);
                a.setFill(BRANCO);
                b.setFill(BRANCO);
                c.setFill(BRANCO);
                return new Group(a, b, c);
            }
        }
    }
}
