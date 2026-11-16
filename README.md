# Chat Multiusuário (Java)

Chat cliente-servidor em Java: **sockets + threads + JDBC/SQLite**, com cliente gráfico JavaFX e cliente de terminal.
Atividade de POO (UniCesumar/UNIPA Curitiba): servidor com várias conexões, broadcast, histórico persistido em banco.

> **VERSÃO INTERMEDIÁRIA — não é a entrega final.** Veja "Estado desta versão" no fim: o build Maven real e a interface JavaFX
> **ainda não foram executados**. Rode `mvn clean test` na sua máquina e me diga o resultado.

## Requisitos
- JDK 21 e Maven 3.9+ (o Maven baixa JavaFX, SQLite JDBC e JUnit sozinho).

## Como executar
**1. Servidor** (cria o banco `chat.db` e as tabelas sozinho, a partir de `src/main/resources/sql/schema.sql`):
```
mvn compile exec:java -Dexec.args="5000 chat.db"
```
**2. Clientes** (um terminal por usuário; abra 3 para a demonstração):
```
mvn javafx:run                                                              # cliente gráfico
mvn compile exec:java@console -Dexec.args="localhost 5000 ana segredo1 novo"   # cliente de terminal
```
No cliente de terminal, o último argumento `novo` cria a conta; sem ele faz login. Digite para falar na sala Geral e `/sair` para sair.
No cliente gráfico, "Configurar servidor" (tela de login) troca endereço/porta (padrão `localhost:5000`).
Preferências do cliente gráfico (tema, servidor, último usuário; nunca a senha): `~/.chat-multiusuario/preferencias.properties`.

## Sala Geral = o broadcast do PDF
Todo usuário que se cadastra ou entra é incluído na **sala Geral**. Mensagem enviada nela é recebida por **todos os usuários conectados**
(é o broadcast exigido pela atividade). Ao autenticar, o servidor envia sozinho as últimas 50 mensagens da Geral (via
`MensagemRepository.buscarUltimas(n)`), sem o cliente pedir. O remetente também recebe uma cópia de confirmação da própria mensagem
(a versão já gravada, com id e horário do servidor), uma única vez; os demais recebem exatamente uma cópia.
Conexões que ainda não fizeram login não recebem mensagens.

Conversas particulares e grupos são **extras** (não exigidos pelo PDF): isolados por participante, não entram no histórico da Geral
e não interferem no broadcast. O nome de grupo "Geral" é reservado.

## Protocolo (uma linha por mensagem, UTF-8, campos separados por TAB)
Dentro de um campo, `\` vira `\\`, TAB vira `\t` e quebra de linha vira `\n`.

| Cliente envia | Servidor responde |
|---|---|
| `REGISTRAR usuario senha avatar` | `AUTENTICADO id usuario avatar` + histórico da Geral (`HISTORICO_INICIO`, `MSG`..., `HISTORICO_FIM`) |
| `LOGIN usuario senha` | igual ao anterior |
| `LISTAR_CONVERSAS` | `CONVERSA ...` (uma por conversa) e `FIM_CONVERSAS` |
| `BUSCAR_USUARIOS termo` | `USUARIO id usuario avatar` (um por achado) e `FIM_USUARIOS` |
| `ABRIR_PRIVADA usuarioId` | `CONVERSA ...` (e `CONVERSA_NOVA` para o outro usuário, se online) |
| `CRIAR_GRUPO nome imagem id1,id2` | `CONVERSA ...` (e `CONVERSA_NOVA` para os integrantes online) |
| `ADICIONAR_PARTICIPANTE conversaId usuarioId` | `OK` (e `CONVERSA_NOVA` para quem entrou) |
| `PARTICIPANTES conversaId` | `USUARIO ...` e `FIM_USUARIOS` |
| `ENTRAR conversaId` | `HISTORICO_INICIO`, `MSG ...` (últimas 50), `HISTORICO_FIM` |
| `MSG conversaId texto` | `MSG ...` para todos os participantes online da conversa |
| `AVATAR avatarId` | `PERFIL_ATUALIZADO id usuario avatar` (grava no banco) |
| `SAIR` | `OK` e a conexão é encerrada |

Erros: `ERRO codigo mensagem`. Formato de `MSG`: `MSG id conversaId remetenteId remetenteNome remetenteAvatar instante conteudo`.

## Arquitetura
- `model` (Usuario, Mensagem, Conversa) · `persistence` (Database, UsuarioRepository, ConversaRepository, **MensagemRepository** — todo o SQL fica aqui) ·
  `security` (PBKDF2) · `service` (ServicoChat: regras e controle de acesso) · `protocol` (Protocolo, Comando, Eventos).
- `server`: `Servidor` (socket de escuta + laço de `accept`, uma thread por cliente) · `ClienteHandler` (`Runnable`, uma conexão) ·
  `RegistroSessoes` (lista sincronizada de conexões) · `Roteador` (grava e entrega) · `ProcessadorComandos` (protocolo) · `ServidorApp` (main).
- `client` (sem JavaFX): `ClienteChat` (protocolo + estado), `ConexaoServidor`, `ClienteConsole`, `ModeloConversas`, `AgrupadorMensagens`, `Avatares`, `Tema`, `Preferencias`.
- `client.ui` (JavaFX): `ChatApp`, `LoginView`, `MainView`, `BarraLateral`, `PainelConversa`, `MensagensView`, diálogos, `AvatarView`, `Icones`.
  A interface só usa o `ClienteChat`; CSS em `src/main/resources/css` (`app.css` + `tema-claro.css`/`tema-escuro.css`). Não há FXML: a interface é montada em código.

## Testes
```
mvn clean test
```
143 métodos `@Test`: regras de negócio sem rede, protocolo, roteamento e isolamento, servidor com sockets reais, cliente (`ClienteChat`,
terminal), persistência JDBC com SQLite real (`RepositoriosTest`, `ServidorSqliteIntegracaoTest`), temas/CSS e conferências estáticas da interface.

## Estado desta versão (o que foi e o que não foi verificado)
**Verificado, mas só com um runner próprio (NÃO é o JUnit real):**
- 127 testes com repositórios em memória e sockets reais em localhost: 127 passaram (2 rodadas).
- 16 testes (`RepositoriosTest` + `ServidorSqliteIntegracaoTest`) executando o código JDBC real dos repositórios contra um **motor SQLite real**
  (o do Python, por uma ponte local, porque o driver `sqlite-jdbc` não pôde ser baixado): 16 passaram. Cobrem 3 clientes, broadcast na Geral,
  histórico ao entrar, privadas/grupos sem interferência, queda abrupta de cliente, reinício do servidor sem perder histórico e sem duplicar a Geral.
- Ensaio manual: `ServidorApp` + 3 processos `ClienteConsole` trocando mensagens, o terceiro recebendo o histórico ao entrar.
- Mutações propositais no código (entrada na Geral, entrega duplicada, histórico no login) foram detectadas pelos testes.

**NÃO verificado:**
- `mvn clean test`, `mvn clean package` e `mvn javafx:run` **nunca foram executados** (sem rede e sem Maven no ambiente de desenvolvimento).
- A interface JavaFX foi compilada **apenas contra classes de apoio que escrevi imitando a API**; nunca foi compilada com o JavaFX real nem aberta.
  Aparência, layout, animações, diálogos e reconexão na tela são **não verificados**.
- O driver `sqlite-jdbc` real e o JUnit real não foram usados.
- O vídeo com 3 clientes não foi gravado.

**Pendente de escrever:** relatório de arquitetura e concorrência, relatório de conformidade com o PDF e roteiro do vídeo (em `docs/`).

## Limitações conhecidas
- Contadores de não lidas e prévias consideram só mensagens recebidas na sessão atual; sem indicador de "online".
- Sem limite de tentativas de login e sem TLS (use em rede local).
- Qualquer integrante de um grupo pode adicionar participantes; não há "sair do grupo".
- A senha fica só na memória do cliente durante a sessão (para reconectar) e é apagada ao sair.
