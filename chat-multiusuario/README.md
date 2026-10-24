# Chat Multiusuário (Java + Swing + MySQL)

Projeto do 2º bimestre, opção A. Estado atual: **semanas 1 a 4** (modelo, pacotes, servidor
com várias conexões e broadcast sincronizado). A integração do histórico com o servidor
(semana 5) ainda não foi feita.

## Como executar

São dois programas, cada um com a sua janela:

1. **Servidor** — execute `server.JanelaServidor` e clique em **Iniciar servidor** (porta 5000).
2. **Clientes** — execute `client.JanelaChat` **uma vez para cada participante**
   (no IntelliJ/Eclipse, rode a classe de novo; cada execução abre uma janela nova).
   Informe servidor (`localhost`), porta e um nome.

Pelo terminal, com Maven:

```
mvn compile
mvn exec:java -Dexec.mainClass=server.JanelaServidor
mvn exec:java -Dexec.mainClass=client.JanelaChat      # em outro terminal, repetir por cliente
```

Para testes unitários: `mvn test`.

## Banco de dados (MySQL)

1. Rode o script `sql/schema.sql` no MySQL (cria o banco `chat_db` e a tabela `mensagens`).
2. A conexão padrão é `localhost:3306`, usuário `root`, senha vazia. Para mudar, defina as
   variáveis de ambiente `CHAT_DB_URL`, `CHAT_DB_USUARIO` e `CHAT_DB_SENHA`.

`persistence.MensagemRepository` já tem `salvar()` e `buscarUltimas(n)`, mas ainda não é
usada pelo servidor (isso entra na semana 5).

## Estrutura

```
src/main/java
  model/        Usuario, Mensagem, Protocolo
  server/       Servidor, ClienteHandler, GerenciadorClientes, Destinatario, JanelaServidor
  client/       ClienteChat (rede), JanelaChat (tela)
  persistence/  MensagemRepository (JDBC), ConfiguracaoBanco, RepositorioException
src/test/java   testes JUnit 5 (não usam rede)
sql/schema.sql  criação da tabela
```

## Como a concorrência é tratada (base para o relatório)

- Cada cliente é atendido por um `ClienteHandler` em uma **thread própria**; o `Servidor`
  só aceita conexões e entrega cada uma a uma nova thread.
- A lista de clientes conectados é o recurso compartilhado. Fica no `GerenciadorClientes`,
  e todo acesso (registrar, remover, broadcast) é feito dentro de `synchronized (trava)`.
- Como o broadcast acontece dentro da região protegida, dois clientes enviando ao mesmo
  tempo não misturam as mensagens, e todos recebem na mesma ordem.
- O `GerenciadorClientes` conhece apenas a interface `Destinatario`, não sockets; por isso
  o broadcast é testado com clientes de mentira, sem rede.
- Se um envio falha (conexão caiu), o cliente é removido e os demais seguem normalmente.
