# DAO e BO — Embarque Certo API

A primeira entrega implementa CRUD de Estacao, Linha, Ocorrencia e RegistroLotacao, com EntityManager nas DAOs e regras/transações nas BOs.

As 11 entidades e os três IDs compostos existentes foram preservados. A validação anterior do mapeamento no Oracle com ddl-auto=validate foi confirmada pelo usuário; não foi repetida nesta entrega. Nenhuma conexão ao Oracle real foi aberta para desenvolver ou testar estas camadas.

## Arquivos criados

Os caminhos Java abaixo são relativos ao pacote `br.com.fiap.embarqueCertoAPI`.

### Produção — 17 arquivos em src/main/java

- `bo/EstacaoBO.java`
- `bo/LinhaBO.java`
- `bo/OcorrenciaBO.java`
- `bo/RegistroLotacaoBO.java`
- `bo/ValidacaoBO.java`
- `config/RelogioConfig.java`
- `dao/EstacaoDAO.java`
- `dao/LinhaDAO.java`
- `dao/LinhaEstacaoDAO.java`
- `dao/OcorrenciaDAO.java`
- `dao/OperadoraDAO.java`
- `dao/RegistroLotacaoDAO.java`
- `dao/TipoOcorrenciaDAO.java`
- `dao/UsuarioDAO.java`
- `exception/ConflitoException.java`
- `exception/RegistroNaoEncontradoException.java`
- `exception/RegraNegocioException.java`

### Testes — 15 arquivos em src/test/java

- `bo/BoTestSupport.java`
- `bo/EstacaoBOTests.java`
- `bo/LinhaBOTests.java`
- `bo/OcorrenciaBOTests.java`
- `bo/RegistroLotacaoBOTests.java`
- `bo/TransacoesBoTests.java`
- `dao/DaoJpqlTests.java`
- `dao/EstacaoDAOTests.java`
- `dao/LinhaDAOTests.java`
- `dao/LinhaEstacaoDAOTests.java`
- `dao/OcorrenciaDAOTests.java`
- `dao/OperadoraDAOTests.java`
- `dao/RegistroLotacaoDAOTests.java`
- `dao/TipoOcorrenciaDAOTests.java`
- `dao/UsuarioDAOTests.java`

`BoTestSupport` contém apenas fixtures, Validator e Clock de teste. As demais 14 classes contêm os testes novos.

Este documento, `DAO-BO.md`, também foi criado. Nenhum arquivo anterior foi modificado nesta entrega: 34 arquivos existentes foram conferidos por SHA-256, incluindo o Model, os testes anteriores, pom.xml, application.properties, scripts e ferramentas.

## Responsabilidades e contratos

Fluxo preparado para os futuros endpoints: Controller → BO → DAO → EntityManager.

| Camada | Responsabilidade |
|---|---|
| DAO | find, consultas JPQL tipadas e parametrizadas, persist, flush e remove |
| BO | validação do Model, existência das referências, vínculo linha–estação, unicidades, datas e coordenação transacional |
| Exceções | distinguir ausência, regra inválida e conflito, sem acoplamento HTTP |
| RelogioConfig | fornecer Clock no fuso America/Sao_Paulo |
| ValidacaoBO | auxiliar interno para reaproveitar constraints do Model e validar IDs |

Cada uma das quatro BOs oferece:

```java
T cadastrar(T dados);
T atualizar(Long id, T dados);
T buscarPorId(Long id);
List<T> listar();
void excluir(Long id);
```

As quatro DAOs principais oferecem `buscarPorId`, `listar`, `inserir`, `atualizar` e `excluir`. As listagens são ordenadas pelo ID e retornam lista vazia quando não há resultados.

OperadoraDAO, TipoOcorrenciaDAO e UsuarioDAO oferecem somente `buscarPorId`. LinhaEstacaoDAO oferece `existePorId(LinhaEstacaoId)`.

DAO usa `@Repository` e `@PersistenceContext`. BO usa `@Service` e injeção por construtor. Não foram criadas interfaces extras, DAOs genéricas ou dependências novas.

## Cadastro e atualização

- Cadastro exige ID fornecido, com a precisão validada pelo Model. Zero e negativos permanecem permitidos nos IDs conforme o DDL.
- ID existente provoca ConflitoException antes de qualquer persistência.
- A BO constrói uma entidade nova para cadastro e utiliza somente persist. Não existe merge, upsert ou geração de ID.
- Atualização exige um registro existente. O ID na entrada pode ser omitido; se informado, deve coincidir com o ID do argumento.
- Atualizar um ID inexistente provoca RegistroNaoEncontradoException e não cadastra um novo registro.
- A BO monta e valida um candidato separado. Somente após validar atributos, referências, vínculo e unicidades copia os campos editáveis para a entidade gerenciada.
- O ID não é copiado na atualização. Campos opcionais informados como null são limpos; os demais atributos editáveis são substituídos pelos valores recebidos.
- A DAO exige entidade gerenciada para atualizar ou excluir. Atualização usa dirty checking e flush.

Uma consulta preliminar não elimina disputas entre transações. As PKs, unicidades e FKs existentes continuam sendo a proteção final. Falhas no persist, flush ou commit são propagadas; não há tentativa de sobrescrever ou repetir o cadastro com outro ID.

## Referências e regras

A BO valida os IDs das referências e carrega as entidades correspondentes. Os objetos relacionados recebidos na entrada são usados somente para obter seus IDs; seus demais atributos não são persistidos ou alterados.

| BO | Regras adicionais às constraints do Model |
|---|---|
| EstacaoBO | nome único, excluindo o próprio ID na atualização |
| LinhaBO | operadora existente e número único, excluindo o próprio ID na atualização |
| OcorrenciaBO | tipo e linha existentes; estação e usuário existentes quando informados; estação pertencente à linha |
| RegistroLotacaoBO | usuário, linha e estação existentes; estação pertencente à linha |

A associação é consultada pelo ID composto completo de LinhaEstacao. Ocorrências que afetam a linha inteira podem ter estação ausente.

Os CHECKs já representados no Model são verificados por Validator: limites, domínios, autor/fonte, status/data final e demais atributos. Não existe confirmação automática por votos, geração de alertas ou atualização de reputação.

### Datas

- Cadastro preenche dtInicio ou dtRegistro quando omitidos.
- Atualização preserva a data existente quando omitida.
- Datas explícitas são aceitas conforme as constraints; datas históricas podem ser informadas.
- Datas dessas operações, incluindo dtFim, são normalizadas para segundos, acompanhando a precisão física Oracle DATE.
- O Clock da aplicação usa America/Sao_Paulo; os testes utilizam instante fixo.

## Exclusões

| Entidade | Dependências verificadas |
|---|---|
| Linha | LinhaEstacao, Ocorrencia, RegistroLotacao |
| Estacao | LinhaEstacao, Ocorrencia, RegistroLotacao, origem ou destino de Trajeto |
| Ocorrencia | Confirmacao, Alerta |
| RegistroLotacao | sem FK de entrada no DDL |

Existência de dependências provoca ConflitoException. Não há exclusão em cascata nem limpeza de registros filhos. A DAO consulta as dependências antes de remove, e as FKs continuam protegendo contra mudanças concorrentes.

## Transações e exceções

As BOs usam `org.springframework.transaction.annotation.Transactional`:

- buscarPorId e listar executam com readOnly=true.
- cadastrar, atualizar e excluir executam em transação de escrita, com propagação REQUIRED padrão.
- As DAOs participam da transação da BO e não abrem transações independentes.
- Não há begin, commit, rollback ou fechamento manual do EntityManager no código de produção.
- flush sincroniza alterações, mas não representa commit.

| Situação | Exceção | Tratamento HTTP futuro |
|---|---|---:|
| Registro principal ausente | RegistroNaoEncontradoException | 404 |
| Atributo, referência ou combinação inválida | RegraNegocioException | 400 |
| Duplicidade detectada ou exclusão bloqueada | ConflitoException | 409 |

As exceções próprias são RuntimeException e possuem construtores com causa. Falhas JPA/Spring não são capturadas genericamente nem convertidas em sucesso, ausência ou conflito sem classificação. A tradução padrão de @Repository pode acrescentar uma exceção Spring à cadeia, preservando a causa original.

O futuro tratamento central deverá classificar violações de constraints e falhas que apareçam no commit, incluindo disputas de unicidade. Esse tratamento HTTP não foi implementado nesta etapa.

## Testes executados

Maven concluiu a compilação e a suíte completa com **BUILD SUCCESS**.

| Grupo | Testes |
|---|---:|
| BOs | 87 |
| DAOs | 58 |
| Consultas JPQL offline | 16 |
| Transações com proxies Spring | 28 |
| Testes anteriores preservados | 276 |
| **Total** | **465** |

**189 testes novos. Falhas: 0. Erros: 0. Ignorados: 0.**

- As DAOs foram exercitadas com EntityManager e TypedQuery simulados.
- As BOs foram exercitadas com DAOs simuladas, Validator real e Clock fixo.
- Os testes verificam candidatos inválidos sem alteração prévia da entidade gerenciada, referências, pertencimento à linha, duplicidades, exclusões, datas, IDs e propagação das causas originais.
- As 15 consultas JPQL de produção foram compiladas com Hibernate, as 11 entidades e o dialeto Oracle. O 16º teste verifica a cobertura de todas elas. Nenhuma consulta foi executada; um ConnectionProvider falha imediatamente se houver tentativa de conexão.
- Os testes transacionais utilizam proxies reais do Spring e um gerenciador transacional de teste, sem DataSource, para observar leitura, escrita, commit, rollback e falhas no commit.
- O teste de contexto existente continua com DataSource simulado e verifica que getConnection não foi chamado. O health permanece aprovado.

Esses testes comprovam lógica, consultas compiláveis e demarcação transacional. Não constituem teste de escrita, commit ou rollback no Oracle real.

Comando utilizado no PowerShell:

```powershell
& 'C:\Users\Samsung\.m2\wrapper\dists\apache-maven-3.10.0-bin\39q0qgmngdu329psje7bjgne79\apache-maven-3.10.0\bin\mvn.cmd' -o '-Dmaven.repo.local=C:/Users/Samsung/.m2/repository' -l target/dao-bo-full-tests.log test
```

Relatórios: `target/surefire-reports/`. Log completo: `target/dao-bo-full-tests.log`.

O ambiente restrito inicialmente bloqueou os locks do cache Maven. A execução autorizada com acesso ao cache resolveu essa limitação, sem alteração do pom.xml ou download de dependências.

## Preparação para a próxima etapa

As entidades devolvidas pelas BOs são objetos internos. Os futuros Controllers utilizarão DTOs, com montagem dos dados de saída dentro da transação da BO para lidar corretamente com relacionamentos LAZY.

O CRUD de Linha e Estacao não cria vínculos em LinhaEstacao. A manutenção da malha, o cadastro de usuários e trajetos, confirmações e alertas continuam para etapas posteriores.

Não foram criados Controllers, DTOs, autenticação, rotinas automáticas de escrita ou mudanças no schema. Nenhum script de preparação foi executado. Não houve commit nem push.
