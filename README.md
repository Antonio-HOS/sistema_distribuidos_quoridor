<!--
  Função deste arquivo: ser a documentação principal do projeto.
  Ele explica o que é o jogo, as regras implementadas, a arquitetura
  distribuída com Java RMI e o passo a passo para compilar, iniciar o
  servidor e conectar os 4 clientes (interface gráfica e modo console).
-->
# Quoridor distribuído com Java RMI

Implementação de Quoridor para **4 jogadores**, usando:

- Java 17
- Java RMI
- 1 processo servidor
- 4 processos clientes independentes
- Interface gráfica Swing (cliente) + modo console opcional
- Nenhum `Socket` criado pela aplicação

## Regras implementadas

- Tabuleiro 9x9.
- 4 jogadores.
- Cada jogador começa com 5 paredes.
- Jogadores começam no meio de cada lado:
  - P1: topo, objetivo chegar à linha inferior.
  - P2: direita, objetivo chegar à coluna esquerda.
  - P3: baixo, objetivo chegar à linha superior.
  - P4: esquerda, objetivo chegar à coluna direita.
- Movimento ortogonal.
- Saltos sobre jogadores.
- Movimento diagonal ao redor de um jogador quando o salto direto está bloqueado.
- Paredes horizontais e verticais.
- Uma parede ocupa duas arestas adjacentes.
- Uma parede não pode cruzar/sobrepor outra.
- Uma parede nunca pode eliminar o caminho de algum jogador até seu objetivo.
- O servidor controla o estado e valida todas as jogadas.
- A partida começa quando os 4 clientes entram.
- Se um jogador sai (`sair`) ou o processo do cliente encerra, a partida termina e os demais são avisados de quem se desconectou.

## Arquitetura

```text
                   RMI Registry
                        |
                  GameService
                        |
                 +------+------+
                 |             |
             Servidor       Estado do jogo
                 |
       +---------+---------+---------+
       |         |         |         |
     Cliente1 Cliente2  Cliente3  Cliente4
```

O código da aplicação não utiliza `java.net.Socket`.

## Executar

Requisitos:

```text
Java 17+
Maven 3.8+
```

### 1. Compilar

```bash
mvn clean package
```

### 2. Iniciar o servidor

```bash
java -cp target/classes br.uesc.quoridor.server.ServerMain
```

O servidor cria o RMI Registry na porta 1099 e publica o serviço `Quoridor`.

### 3. Iniciar quatro clientes

Em quatro terminais diferentes (abre a interface gráfica):

```bash
java -cp target/classes br.uesc.quoridor.client.ClientMain
```

Ou com host/porta já definidos:

```bash
java -cp target/classes br.uesc.quoridor.client.ClientMain localhost 1099
```

Cada janela pede nome, host e porta do Registry (se não forem passados por argumento).

Se servidor e clientes estiverem na mesma máquina, use `localhost` e porta `1099`.
Em máquinas diferentes, informe o IP do computador que executa o servidor.

### Interface gráfica

- **Mover**: clique numa casa marcada em azul. Casa vizinha livre anda um passo. Com outra peça à frente, clique na casa atrás dela para pular; se uma parede bloquear essa passagem, clique numa casa ao lado da peça. As setas fazem o passo ou o pulo reto.
- **Parede H / Parede V**: selecione o modo e clique no vão entre as casas (coordenadas 0..7).
- O tabuleiro atualiza sozinho enquanto espera os outros jogadores.

### Modo console (opcional)

```bash
java -cp target/classes br.uesc.quoridor.client.ClientMain --console
```

Comandos:

```text
m cima | m baixo | m esquerda | m direita | m LINHA COLUNA
p h LINHA COLUNA | p v LINHA COLUNA
estado | ajuda | sair
```

Exemplos: `m baixo`, `p h 2 3`, `p v 4 6`.

Para uma parede:

- `h 2 3` — horizontal com canto superior esquerdo em `(2,3)`.
- `v 4 6` — vertical com canto superior esquerdo em `(4,6)`.

Casas: `0..8`. Paredes: linha/coluna `0..7`.

## Observação acadêmica

A aplicação foi estruturada para deixar claro o papel do RMI:

- `GameService` é a interface remota.
- `GameServiceImpl` é o objeto remoto no servidor.
- Clientes fazem lookup do objeto no Registry.
- O estado retornado é serializável.
- O servidor é a autoridade para validar movimentos e paredes.

O RMI utiliza a infraestrutura de comunicação do Java internamente, mas a aplicação não abre nem gerencia `Socket` diretamente.
