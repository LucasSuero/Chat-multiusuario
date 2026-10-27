# Status do trabalho

**Estado:** implementação completa (semanas 1–6 do cronograma); falta apenas a validação em ambiente real (Maven + MySQL) e o vídeo.

## Alterações desta etapa
- `persistence/RepositorioMensagens.java` (novo): interface do repositório; `MensagemRepository` a implementa.
- `server/ServicoChat.java` (novo): grava, transmite e envia o histórico, com trava de ordem.
- `server/ClienteHandler.java`, `server/Servidor.java`: passam a usar `ServicoChat`; `Servidor` verifica o banco em segundo plano.
- `src/test/java/server/ServicoChatTest.java` (11 testes) e `src/test/java/integration/ChatIntegracaoTest.java` (5 testes, tag `integracao`).
- `ClienteHandler`: fila de saída + thread de escrita por cliente (cliente parado não trava o broadcast; reproduzido e corrigido), leitura de linha limitada; `Servidor`: pausa no `accept()` após erro.
- `pom.xml`: propriedade `testes.excluidos` (integração fora do `mvn test` comum).
- `README.md` reescrito (roteiro de demonstração); `RELATORIO.md` criado. `target/` removido do ZIP.

## Validação realmente executada (ambiente sem Maven, sem JUnit, sem MySQL, sem rede)
- Compilação de `src/main` com `javac` (JDK 21, `-Xlint:all`): sem erros nem avisos.
- 38 testes (33 unitários + 5 de integração = 38) executados com um mini-executor próprio que imita as anotações/asserções do JUnit: 38 passaram, repetido 8 vezes sem falha. **Não foi JUnit real nem `mvn test`.**
- Servidor real sem banco/driver: chat funcionou e o log avisou da falha.
- **Não executado:** `mvn test`, qualquer acesso real ao MySQL (`MensagemRepository`, `schema.sql`), interface Swing.

## Próximo passo
`mysql < sql/schema.sql`, depois `mvn test` e `mvn test -Dtestes.excluidos=nenhum`; seguir o roteiro do README com 3 clientes e gravar o vídeo.
