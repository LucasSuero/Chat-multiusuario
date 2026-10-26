# Chat Multiusuário com Histórico (Java + Sockets + JDBC/MySQL + Swing)

Projeto de Programação Orientada a Objetos. Servidor atende vários clientes em threads
separadas, retransmite as mensagens (broadcast), grava tudo no MySQL via JDBC e entrega as
últimas 50 mensagens a quem acabou de entrar. Arquitetura e concorrência: ver `RELATORIO.md`.

## Preparação

1. **Java 17+** e **Maven**. No Eclipse, importe como *Existing Maven Projects* (o `.classpath`
   que acompanha o projeto não lista as dependências do `pom.xml`).
2. **MySQL 8+**: rode `sql/schema.sql` (cria o banco `chat_db` e a tabela `mensagens`).
3. Conexão padrão: `localhost:3306`, usuário `root`, senha vazia. Para mudar, defina as
   variáveis de ambiente `CHAT_DB_URL`, `CHAT_DB_USUARIO` e `CHAT_DB_SENHA`.

Sem banco o chat continua funcionando, mas nada é gravado e o histórico vem vazio (o log do
servidor avisa).

## Roteiro de demonstração (3 clientes)

```
mvn compile
mvn exec:java -Dexec.mainClass=server.JanelaServidor      # terminal 1
mvn exec:java -Dexec.mainClass=client.JanelaChat          # terminais 2, 3 e 4
```

(Pela IDE: execute `server.JanelaServidor` uma vez e `client.JanelaChat` uma vez por participante.)

1. No servidor, clique em **Iniciar servidor** (porta 5000). O log deve dizer *"Banco de dados conectado"*.
2. Abra três clientes (`localhost`, `5000`) com os nomes Ana, Bia e Caio. Os três veem os avisos de entrada.
3. Troque mensagens: cada uma aparece para todos, com `[hora] remetente: texto`.
4. Feche a janela da Bia (desconexão abrupta): os outros veem "Bia saiu" e continuam conversando; o contador do servidor cai para 2.
5. Abra um quarto cliente (Duda): ele recebe o *Histórico recente* com as mensagens anteriores.
6. Tente entrar com um nome em uso (ex.: `ana`): a entrada é recusada com mensagem clara.

## Testes

```
mvn test                                  # testes unitários (sem rede e sem banco)
mvn test -Dtestes.excluidos=nenhum        # inclui o teste de integração com sockets em localhost
```

- `MensagemTest`, `UsuarioTest`: regras do modelo (formatação, validações).
- `GerenciadorClientesTest`: broadcast e lista sincronizada, com clientes falsos, inclusive concorrência.
- `ServicoChatTest`: gravação, histórico, falha de banco e ordem, com repositório e clientes falsos.
- `integration.ChatIntegracaoTest`: três clientes reais, histórico, desconexão abrupta, nome repetido.

O `MensagemRepository` (JDBC) não tem teste automatizado porque exige um MySQL rodando;
valide-o com o roteiro acima.

## Estrutura

```
src/main/java
  model/        Usuario, Mensagem, Protocolo
  server/       Servidor, ClienteHandler, ServicoChat, GerenciadorClientes, Destinatario, JanelaServidor
  client/       ClienteChat (rede), JanelaChat (tela)
  persistence/  RepositorioMensagens (interface), MensagemRepository (JDBC), ConfiguracaoBanco, RepositorioException
src/test/java   testes JUnit 5
sql/schema.sql  criação do banco e da tabela
RELATORIO.md    relatório curto (arquitetura e concorrência)
```
