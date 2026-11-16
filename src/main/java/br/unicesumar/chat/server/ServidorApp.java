package br.unicesumar.chat.server;

import br.unicesumar.chat.persistence.ConversaRepository;
import br.unicesumar.chat.persistence.Database;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.service.ServicoChat;
import java.nio.file.Path;
import java.time.Clock;

/** Ponto de entrada do servidor. Uso: ServidorApp [porta] [arquivo.db]  (padrão: 5000 e chat.db). */
public final class ServidorApp {

    private ServidorApp() {
    }

    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        Path arquivo = Path.of(args.length > 1 ? args[1] : "chat.db");

        Database db = Database.emArquivo(arquivo);
        db.inicializar();

        ServicoChat servico = new ServicoChat(new UsuarioRepository(db), new ConversaRepository(db),
                new MensagemRepository(db), Clock.systemUTC());
        RegistroSessoes registro = new RegistroSessoes();
        Roteador roteador = new Roteador(servico, registro);
        Servidor servidor = new Servidor(porta, new ProcessadorComandos(servico, roteador), registro);

        Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar, "encerramento"));
        servidor.iniciar();
        System.out.println("Servidor no ar na porta " + servidor.porta() + " (banco: " + arquivo.toAbsolutePath() + ")");
        System.out.println("Pressione Ctrl+C para encerrar.");
    }
}
