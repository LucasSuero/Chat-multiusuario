# Relatório — Chat Multiusuário com Histórico

## 1. Visão geral

Sistema cliente-servidor em Java. O servidor aceita vários clientes ao mesmo tempo, retransmite
cada mensagem a todos (broadcast), grava as mensagens no MySQL via JDBC e entrega o histórico
recente a quem acabou de entrar. A comunicação é por linhas de texto UTF-8 sobre TCP: o cliente
envia o nome, o servidor responde `OK` ou `ERRO:motivo`, e depois cada linha enviada é uma
mensagem e cada linha recebida já vem formatada (`[14:35] Ana: oi`).

## 2. Arquitetura

| Pacote | Classe | Responsabilidade |
|---|---|---|
| `model` | `Usuario`, `Mensagem`, `Protocolo` | Entidades e regras de domínio (nome válido, mensagem com remetente, conteúdo e horário, formatação). Sem rede e sem banco. |
| `server` | `Servidor` | Socket de escuta e laço de `accept()`; uma thread por conexão. |
| `server` | `ClienteHandler` | `Runnable` que atende **uma** conexão: lê as linhas do cliente e as repassa ao `ServicoChat`. |
| `server` | `ServicoChat` | Regras do chat: gravar, transmitir, entrar e receber histórico. |
| `server` | `GerenciadorClientes` | Lista de clientes conectados e broadcast, com acesso sincronizado. Conhece só a interface `Destinatario`. |
| `persistence` | `MensagemRepository` | Único ponto com SQL/JDBC (`salvar`, `buscarUltimas`), atrás da interface `RepositorioMensagens`. |
| `client` | `ClienteChat`, `JanelaChat` | Conexão e envio/recebimento; tela Swing. |

Separar `GerenciadorClientes`/`ServicoChat` de `ClienteHandler` e usar as interfaces `Destinatario`
e `RepositorioMensagens` reduz o acoplamento: as regras de negócio são testadas com objetos
falsos, sem rede e sem banco. Em relação à estrutura sugerida, a lista de clientes fica em
`GerenciadorClientes` (usado pelo `Servidor` via `ServicoChat`), e não dentro de `Servidor`.

## 3. Concorrência

- **Uma thread por cliente.** `Servidor` só aceita conexões; cada `ClienteHandler` roda em sua
  thread, então um cliente lento ou parado não impede os demais de enviar.
- **Lista compartilhada.** Todo acesso à lista de clientes (registrar, remover, broadcast,
  contar) acontece dentro de `synchronized (trava)` em `GerenciadorClientes`, evitando condições
  de corrida. O registro verifica nome repetido e adiciona na mesma região protegida.
- **Escrita em cada socket.** Cada `ClienteHandler` tem sua própria trava de escrita, para que duas
  mensagens não se misturem no mesmo fluxo.
- **Ordem e histórico consistentes.** `ServicoChat` usa uma trava (`ordem`) em torno de
  "criar a mensagem (horário) + gravar + transmitir" e de "registrar o cliente + enviar o
  histórico". Consequências: a ordem gravada no banco é igual à ordem entregue, e um cliente
  novo recebe o histórico e passa às mensagens ao vivo sem perder nem repetir nenhuma
  (coberto por `ServicoChatTest`).
- **JDBC.** `MensagemRepository` abre e fecha uma conexão por operação, sem estado compartilhado
  entre threads.
- **Desconexões.** Falha de leitura/escrita é tratada no `ClienteHandler` e no `broadcast`: o
  cliente é removido, o socket fechado e os demais seguem. O servidor não cai.
- **Banco fora do ar.** O erro vai para o log; o chat continua, só sem gravar/histórico.

## 4. Decisões e limitações conhecidas

- Só mensagens de usuários são gravadas; avisos do sistema ("entrou/saiu") não.
- O histórico traz as últimas 50 mensagens (`ServicoChat.TAMANHO_HISTORICO`).
- Gravar dentro da trava de ordem serializa as gravações: é simples e correto para este porte,
  mas um banco lento atrasa o chat todo. Um pool de conexões ou uma fila de gravação seria a evolução.
- O broadcast escreve nos sockets dentro da trava da lista. Se um cliente parar de ler sem fechar a
  conexão e o buffer TCP encher, a escrita pode bloquear os demais. Não foi tratado (exigiria fila
  de saída por cliente); não foi reproduzido nos testes.
- Linhas recebidas não têm tamanho máximo na leitura (o limite de 500 caracteres é validado depois).
- Sem autenticação: o nome é apenas um apelido único por sessão.
