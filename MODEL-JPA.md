# Camada Model/JPA — Embarque Certo API

Implementação baseada na leitura de `scripts/01_DDL_EmbarqueCerto.sql` e na conferência dos scripts existentes. O DDL foi lido como texto; nenhum script de preparação foi executado contra o Oracle.

## Arquivos criados

- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Operadora.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Estacao.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Usuario.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/TipoOcorrencia.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Linha.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/LinhaEstacao.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Ocorrencia.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Confirmacao.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/RegistroLotacao.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Trajeto.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/Alerta.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/id/LinhaEstacaoId.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/id/ConfirmacaoId.java`
- `src/main/java/br/com/fiap/embarqueCertoAPI/model/id/AlertaId.java`
- `src/test/java/br/com/fiap/embarqueCertoAPI/model/CompositeIdTests.java`
- `src/test/java/br/com/fiap/embarqueCertoAPI/model/ModelValidationTests.java`
- `src/test/java/br/com/fiap/embarqueCertoAPI/model/ModelMappingTests.java`
- `MODEL-JPA.md` (este documento).

Arquivo existente alterado: `src/test/java/br/com/fiap/embarqueCertoAPI/EmbarqueCertoApiApplicationTests.java`. O contexto completo agora usa um `DataSource` simulado, desabilita inicialização SQL e geração de schema e verifica que nenhuma conexão JDBC foi solicitada. Também verifica o GET `/api/health` por MockMvc.

## Entidades e relacionamentos

| Entidade | Tabela | Referências ManyToOne |
|---|---|---|
| Operadora | T_EC_OPERADORA | — |
| Linha | T_EC_LINHA | operadora |
| Estacao | T_EC_ESTACAO | — |
| LinhaEstacao | T_EC_LINHA_ESTACAO | linha, estacao |
| Usuario | T_EC_USUARIO | — |
| TipoOcorrencia | T_EC_TIPO_OCORRENCIA | — |
| Ocorrencia | T_EC_OCORRENCIA | tipoOcorrencia, linha, estacao (opcional), usuario (opcional) |
| Confirmacao | T_EC_CONFIRMACAO | ocorrencia, usuario |
| RegistroLotacao | T_EC_REGISTRO_LOTACAO | usuario, linha, estacao |
| Trajeto | T_EC_TRAJETO | usuario, estacaoOrigem, estacaoDestino |
| Alerta | T_EC_ALERTA | trajeto, ocorrencia |

As 17 FKs têm nomes explícitos iguais aos do DDL. Todos os relacionamentos usam `LAZY`, são unidirecionais e não possuem cascatas. Os 15 obrigatórios possuem `optional=false`, `nullable=false` e `@NotNull`.

As associativas usam `@EmbeddedId` e duas referências `@MapsId` cada. As três chaves `@Embeddable` implementam `Serializable`, têm componentes escalares `Long` e implementam igualdade e hash pelos dois componentes. A validação recursiva com `@Valid` está somente nas chaves.

As oito unicidades têm nomes e colunas explícitos. Não foi fixado schema nas tabelas.

## Tipos e validações

- IDs atribuídos explicitamente, sem geradores. `Long` respeita as precisões 3, 5, 8 e 10 por `@Digits`. Zero e negativos não foram proibidos, pois o DDL não proíbe esses valores nos IDs.
- Os inteiros usam `Integer`. `@JdbcTypeCode(SqlTypes.NUMERIC)` e definições `NUMBER(p)` mantêm o tipo numérico físico, inclusive para IDs.
- Coordenadas usam `BigDecimal`, `NUMBER(9,6)`, até seis casas decimais e limites geográficos.
- Textos usam `VARCHAR2(n)`, tamanhos explícitos e presença conforme o DDL. Flags usam `String`, `CHAR(1)` e tipo JDBC `CHAR`.
- Datas usam `LocalDateTime` com definição física `DATE`. O acesso JDBC por timestamp preserva horas, minutos e segundos. Oracle DATE não armazena frações de segundo nem fuso horário.
- Horário de partida continua `String` em `HH:mm`. Domínios, flags e faixas numéricas seguem os CHECKs.
- O email é validado segundo o CHECK `LIKE '%_@_%._%'`, sem acrescentar uma regra de email mais restritiva.
- `@AssertTrue` verifica autor/fonte, fim/situação da ocorrência e IDs diferentes para origem/destino.
- Os defaults constantes do DDL são inicializados. As cinco datas com DEFAULT SYSDATE ficam para preenchimento explícito na futura BO; não há callbacks.

Nas colunas compartilhadas por `@MapsId`, o Hibernate utiliza metadados de `@JoinColumn`, que não possui atributos de precisão e escala na API JPA. O tipo físico `NUMBER(p)` fica explícito no JoinColumn. Os testes verificam esse tipo e também a precisão/escala declaradas no componente escalar da chave.

Não foram encontradas divergências nos mapeamentos em relação ao DDL fornecido. A compatibilidade com a instância Oracle em execução ainda depende da validação separada abaixo.

## Testes executados

Compilação Maven e suíte completa concluídas com `BUILD SUCCESS`, Java 25.0.4 e dependências já presentes no cache. Não houve alteração no pom.xml nem adição de dependências.

| Suíte | Testes |
|---|---:|
| CompositeIdTests | 9 |
| ModelValidationTests | 156 |
| ModelMappingTests | 41 |
| EmbarqueCertoApiApplicationTests | 2 |
| OracleTableDiagnosticTests | 16 |
| OracleDatabaseSetupManualTests | 52 |
| **Total** | **276** |

**Falhas: 0. Erros: 0. Ignorados: 0.**

Os 206 testes novos do Model cobrem igualdade/hash/serialização das chaves, obrigatoriedade, limites, opcionais, domínios, condições entre campos e defaults. Os testes de metadados constroem Metadata e SessionFactory Hibernate com OracleDialect, verificando 11 entidades, 65 colunas, 11 PKs, 17 FKs e oito unicidades contra o texto do DDL.

Um ConnectionProvider falha imediatamente se houver tentativa de obter conexão JDBC nos testes de mapeamento. Geração de schema e scripts está desabilitada. Também há testes de binding e extração com JDBC simulado para horário em DATE e IDs NUMBER(10) acima do limite de Integer.

As ferramentas de diagnóstico e preparação continuam com seus testes JDBC simulados. Os resultados desta suíte não constituem validação contra o Oracle real.

Comando utilizado no PowerShell:

```powershell
& 'C:\Users\Samsung\.m2\wrapper\dists\apache-maven-3.10.0-bin\39q0qgmngdu329psje7bjgne79\apache-maven-3.10.0\bin\mvn.cmd' -o '-Dmaven.repo.local=C:/Users/Samsung/.m2/repository' test
```

Os relatórios ficam em `target/surefire-reports/`. O log da execução completa aprovada fica em `target/model-test-run.log`.

## Validação posterior contra o Oracle — não executada

1. Duplicar temporariamente a configuração IntelliJ existente da aplicação principal, mantendo as variáveis de conexão existentes.
2. Adicionar somente estes argumentos de programa:

   ```text
   --spring.jpa.hibernate.ddl-auto=validate
   --spring.sql.init.mode=never
   --spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none
   --diagnostico.oracle.enabled=false
   ```

3. Conferir que não existe outro override de geração de schema, especialmente `jakarta.persistence.schema-generation.database.action`, que possa prevalecer sobre o `validate`. Não iniciar a ferramenta manual de preparação nem os scripts SQL.
4. Iniciar a aplicação e conferir a inicialização do EntityManagerFactory. O modo `validate` consulta metadados e acusa incompatibilidades sem criar ou modificar tabelas e sem inserir, atualizar ou remover registros.
5. Se houver divergência, registrar tabela, coluna e tipo esperado/encontrado, sem credenciais. Ajustar primeiro o mapeamento Java.
6. Encerrar essa execução e descartar a configuração temporária.

Essa etapa verifica estrutura por metadados; uma leitura posterior de registros por JPA poderá conferir os valores e horários existentes em transações somente leitura. Nenhuma validação contra o Oracle real foi realizada nesta implementação.

## Situação da etapa

O código do Model e os testes isolados estão concluídos. Resta a validação de leitura contra o Oracle existente, em execução separada. DAO, BO, DTOs, CRUD, autenticação, deploy e PDF não foram implementados nesta etapa.
