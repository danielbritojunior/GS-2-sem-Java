# API REST — Embarque Certo

Implementação das quatro funcionalidades prioritárias, mantendo o fluxo Controller → BO → DAO → Oracle. A camada REST não acessa EntityManager nem DAO diretamente.

## Arquivos desta entrega

Caminhos Java relativos ao pacote `br.com.fiap.embarqueCertoAPI`.

### Criados em src/main/java — 12 arquivos

- `controller/EstacaoController.java`
- `controller/LinhaController.java`
- `controller/OcorrenciaController.java`
- `controller/RegistroLotacaoController.java`
- `dto/ErroResponse.java`
- `dto/LinhaRequest.java`
- `dto/LinhaResponse.java`
- `dto/OcorrenciaRequest.java`
- `dto/OcorrenciaResponse.java`
- `dto/RegistroLotacaoRequest.java`
- `dto/RegistroLotacaoResponse.java`
- `exception/GlobalExceptionHandler.java`

### Criados em src/test/java — quatro arquivos

- `controller/EstacaoControllerTests.java`
- `controller/LinhaControllerTests.java`
- `controller/OcorrenciaControllerTests.java`
- `controller/RegistroLotacaoControllerTests.java`

Também foi criado este documento, `API-REST.md`.

### Arquivos existentes alterados

- `bo/LinhaBO.java`
- `bo/OcorrenciaBO.java`
- `bo/RegistroLotacaoBO.java`
- `src/main/resources/application.properties`
- `src/test/java/br/com/fiap/embarqueCertoAPI/bo/TransacoesBoTests.java`

As três BOs receberam apenas métodos de resposta DTO e conversores privados. Seus métodos anteriores, construtores e regras de negócio foram preservados. EstacaoBO, ValidacaoBO, as três exceções anteriores, as 11 entidades e todas as DAOs permaneceram inalterados.

## Endpoints

| Entidade | Endereço base |
|---|---|
| Linha | /api/linhas |
| Estacao | /api/estacoes |
| Ocorrencia | /api/ocorrencias |
| RegistroLotacao | /api/registros-lotacao |

Cada endereço base possui cinco operações, totalizando 20:

| Método e endereço | Sucesso |
|---|---|
| GET /recurso | 200 com array; vazio retorna [] |
| GET /recurso/{id} | 200 com objeto |
| POST /recurso | 201 com objeto criado e Location relativo |
| PUT /recurso/{id} | 200 com objeto atualizado |
| DELETE /recurso/{id} | 204 sem corpo |

O endpoint `GET /api/health` permanece disponível e inalterado.

Os Controllers usam as anotações convencionais Spring MVC, injeção por construtor e ResponseEntity. Não possuem transações ou regras de negócio.

## DTOs e transações

Linha, Ocorrencia e RegistroLotacao possuem Request e Response simples. Os relacionamentos são representados por IDs. Não há objetos Operadora, Usuario, Linha ou Estacao aninhados nas respostas dessas três funcionalidades.

Estacao usa diretamente seu Model, pois possui somente atributos escalares, sem relacionamentos.

A conversão Request → entidade é um método privado no Controller. Ela constrói objetos novos e referências contendo somente IDs; a BO carrega e valida essas referências.

As BOs com relacionamentos oferecem os métodos adicionais:

- listarRespostas()
- buscarRespostaPorId(id)
- cadastrarResposta(entidade)
- atualizarResposta(id, entidade)

Esses métodos chamam as operações existentes e montam DTOs antes do encerramento da transação. As listas são completamente convertidas antes de retornar. Leituras usam readOnly=true; cadastro e atualização usam transação de escrita. DELETE continua chamando o método existente.

Não foram criados serviços adicionais, fachadas, classes Mapper ou grupos de validação. As respostas DTO não precisam de sessão JPA aberta durante a serialização.

## Validação e comportamento de escrita

Model e BO continuam responsáveis pelas validações Jakarta e pelas regras: presença e precisão dos IDs, limites, domínios, referências existentes, vínculo linha–estação, autor/fonte, situação/data final, unicidades e exclusões bloqueadas.

- POST exige ID manual e não sobrescreve um registro existente.
- PUT exige um registro existente, não faz upsert e não altera seu ID.
- O ID no corpo do PUT pode ser omitido. Se informado, deve coincidir com a URL.
- PUT substitui os atributos editáveis. Campos opcionais omitidos ou null são limpos, exceto as datas com o comportamento de preservação descrito abaixo.
- Na criação de Ocorrencia, a omissão de nrAtrasoMin e stOcorrencia mantém 0 e ABERTA, respectivamente. Esses defaults também se aplicam quando omitidos no Request de PUT; isso não representa PATCH.
- Na entrada Estacao, a omissão de stAcessivel e nrMinBaldeacao mantém S e 0.
- Null explícito em um desses atributos obrigatórios continua sujeito à validação e não substitui seu valor padrão automaticamente.
- dtInicio e dtRegistro são preenchidos pela BO no cadastro quando omitidos/null e preservados na atualização quando omitidos/null.
- Datas explícitas são aceitas conforme as regras atuais e normalizadas para segundos.
- IDs zero ou negativos não recebem uma proibição nova, pois as constraints do Model/DDL não exigem positividade.

Os campos de data usam ISO 8601 sem offset. A BO utiliza o Clock existente no fuso America/Sao_Paulo.

A existência de dependências bloqueia exclusão, sem cascatas. A proteção contra concorrência continua nas constraints Oracle; falhas de integridade propagadas recebem a resposta genérica 409.

## Exemplos de JSON

Os exemplos abaixo documentam contratos e fixtures ilustrativas. Nenhuma requisição POST, PUT ou DELETE desses exemplos foi executada no Oracle real.

Cada objeto representa um corpo POST válido e o formato de uma resposta individual de GET, POST ou PUT. Datas podem ser omitidas no POST para preenchimento pela BO.

### Linha

```json
{
  "idLinha": 7,
  "idOperadora": 1,
  "nrLinha": 7,
  "nmLinha": "Linha Exemplo",
  "tpModal": "METRO",
  "dsCorHex": "#123456"
}
```

### Estacao

```json
{
  "idEstacao": 16,
  "nmEstacao": "Estação Exemplo",
  "stAcessivel": "S",
  "nrMinBaldeacao": 0,
  "nrLatitude": -23.5503,
  "nrLongitude": -46.6331
}
```

### Ocorrencia

```json
{
  "idOcorrencia": 13,
  "idTipoOcorrencia": 1,
  "idLinha": 3,
  "idEstacao": 9,
  "idUsuario": 1,
  "tpFonte": "USUARIO",
  "dsOcorrencia": "Intervalo maior entre trens",
  "nrAtrasoMin": 10,
  "stOcorrencia": "ABERTA",
  "dtInicio": "2026-11-12T08:30:00",
  "dtFim": null
}
```

### RegistroLotacao

```json
{
  "idRegistro": 21,
  "idUsuario": 1,
  "idLinha": 3,
  "idEstacao": 9,
  "nrNivel": 4,
  "dsSentido": "Centro",
  "dtRegistro": "2026-11-12T08:35:00"
}
```

### Listagem

GET retorna um array dos objetos correspondentes. Uma lista sem resultados retorna:

```json
[]
```

### Atualização

Exemplo de corpo para `PUT /api/ocorrencias/13`, com o ID omitido e atraso alterado:

```json
{
  "idTipoOcorrencia": 1,
  "idLinha": 3,
  "idEstacao": 9,
  "idUsuario": 1,
  "tpFonte": "USUARIO",
  "dsOcorrencia": "Intervalo maior entre trens",
  "nrAtrasoMin": 15,
  "stOcorrencia": "ABERTA",
  "dtInicio": "2026-11-12T08:30:00",
  "dtFim": null
}
```

Para Linha, Estacao e RegistroLotacao, PUT utiliza os mesmos campos dos respectivos exemplos, podendo omitir o ID principal.

### Ocorrência oficial

Para `tpFonte = OPERADORA`, idUsuario deve ser null. idEstacao null representa ocorrência que afeta a linha inteira. Ocorrência ativa exige dtFim null; encerrada/descartada exige fim preenchido e não anterior ao início.

### Criação e exclusão

POST de Ocorrencia com o ID 13 retorna Location `/api/ocorrencias/13` e o objeto criado. DELETE utiliza `/api/ocorrencias/13` e, quando permitido, retorna 204 sem JSON.

O cadastro de Linha/Estacao não cria associação em LinhaEstacao. A manutenção da malha permanece para uma etapa posterior; ocorrências e lotação exigem um vínculo de rede existente quando informam linha e estação.

## Respostas de erro

O GlobalExceptionHandler usa RestControllerAdvice e ResponseEntityExceptionHandler, com ErroResponse contendo status, mensagem e caminho.

| Situação | HTTP |
|---|---:|
| RegraNegocioException e dados rejeitados pelo Model/BO | 400 |
| JSON ausente, inválido, tipo ou data incorreta | 400 |
| RegistroNaoEncontradoException | 404 |
| ConflitoException | 409 |
| DataIntegrityViolationException | 409, com mensagem genérica de integridade |
| Falha inesperada | 500, com mensagem genérica |

Os status próprios do Spring, como 405 e 415, são mantidos.

Exemplo de conflito:

```json
{
  "status": 409,
  "mensagem": "Ja existe uma linha com esse numero",
  "caminho": "/api/linhas"
}
```

Exemplo de erro inesperado:

```json
{
  "status": 500,
  "mensagem": "Falha interna ao processar a requisicao.",
  "caminho": "/api/ocorrencias"
}
```

Não há classificação de códigos Oracle. Mensagens brutas de persistência/conexão, SQL e stack traces não são devolvidos ao cliente. O log de falha inesperada registra somente o tipo da exceção, sem copiar a mensagem bruta. As causas das exceções existentes não foram substituídas.

## Configuração

Foram acrescentadas somente estas propriedades, preservando as linhas anteriores de conexão:

```properties
server.port=${PORT:8080}
spring.jackson.deserialization.accept-float-as-int=false
```

A porta utiliza PORT quando fornecida pelo ambiente. A configuração Jackson funciona com Spring Boot 4.1.2 e Jackson 3.1.7 presentes no projeto: os testes MVC confirmaram que IDs fracionários retornam 400 e coordenadas BigDecimal continuam aceitas.

Não há dependências novas, alterações de credenciais ou configuração de geração de schema nesta entrega. Deploy não foi realizado.

## Testes executados

Compilação Maven e suíte completa: **BUILD SUCCESS**.

| Grupo | Testes |
|---|---:|
| EstacaoControllerTests | 17 |
| LinhaControllerTests | 18 |
| OcorrenciaControllerTests | 24 |
| RegistroLotacaoControllerTests | 20 |
| Novos cenários em TransacoesBoTests | 6 |
| Testes anteriores | 465 |
| **Total** | **550** |

**85 testes novos. Falhas: 0. Erros: 0. Ignorados: 0.**

Os testes MVC exercitam Controllers e BOs reais com DAOs simuladas. Seus contextos verificam a ausência de DataSource. Cobrem GET/lista vazia, POST/Location, PUT/ID omitido, DELETE, validação do Model/BO, referências inválidas, unicidades, dependências, fonte/fim da ocorrência, JSON inválido, números fracionários, datas e erros 400/404/409/500. Também conferem os erros HTTP 405 e 415.

Os seis testes transacionais adicionados reutilizam a infraestrutura de teste existente: proxies Spring, DAOs simuladas e gerenciador transacional sem DataSource. Referências simuladas falham se seus IDs forem acessados fora de uma transação; esses testes confirmam a conversão dentro da BO e a serialização do DTO após o encerramento.

Os testes anteriores de metadados Hibernate continuam bloqueando conexões JDBC. O teste de contexto/health continua utilizando DataSource simulado e verificando que getConnection não foi chamado.

Os testes comprovam os contratos HTTP e o comportamento com dependências simuladas; não representam execução de requisições ou transações contra o Oracle real.

Comando utilizado:

```powershell
& 'C:\Users\Samsung\.m2\wrapper\dists\apache-maven-3.10.0-bin\39q0qgmngdu329psje7bjgne79\apache-maven-3.10.0\bin\mvn.cmd' -o '-Dmaven.repo.local=C:/Users/Samsung/.m2/repository' -l target/rest-full-tests.log test
```

Relatórios: `target/surefire-reports/`. Log da execução completa: `target/rest-full-tests.log`.

O ambiente restrito inicialmente bloqueou os locks do cache Maven. A execução autorizada com acesso ao cache resolveu o impedimento, sem download de dependências ou alteração do pom.xml.

## Validação posterior de leitura no Oracle

Esta verificação não foi executada nesta entrega.

1. Iniciar a aplicação no IntelliJ com a configuração de conexão existente. Manter geração de schema desligada; se desejar repetir a validação de metadados, usar somente ddl-auto=validate nessa execução.
2. Desligar inicialização SQL e manter o diagnóstico automático desabilitado durante os testes HTTP.
3. Executar somente GET: /api/health, os quatro endereços base e buscas por IDs existentes da carga, como 1.
4. Conferir as contagens conhecidas: linhas=6, estações=15, ocorrências=12 e registros de lotação=20, se não houve alterações externas.
5. Conferir IDs relacionados, campos opcionais, datas e ausência de falhas LAZY.
6. Executar GET com parâmetro inválido e com ID inexistente confirmado por leitura.
7. Conferir por SELECT as contagens das 11 tabelas, mantendo o total conhecido de 132 registros.

POST, PUT e DELETE reais ficam para um ambiente isolado e posteriormente autorizado. Não usar o schema atual para testes de escrita, mesmo com intenção de rollback.

Não foram criados JWT, autenticação, paginação ou filtros. Nenhum script de preparação foi executado. Não houve acesso ao Oracle real, deploy, commit ou push.
