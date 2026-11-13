# Preparacao manual do Oracle — Embarque Certo API

Ferramenta temporaria para Java 25 e Spring Boot 4.1.2. A classe
`br.com.fiap.embarqueCertoAPI.maintenance.OracleDatabaseSetupManual` fica em
`src/test/java`: nao e Controller, entidade JPA, ApplicationRunner ou tarefa
executada pela inicializacao normal da API. Ela nao integra o JAR de producao.

O Spring cria apenas o DataSource com as propriedades e variaveis ja existentes.
O contexto manual nao importa a aplicacao principal, nao faz component scan, nao
inicializa JPA e nao habilita a inicializacao automatica de scripts SQL.
Nao e necessario alterar `pom.xml`, `application.properties` ou credenciais.

## Executar a inspecao no IntelliJ

1. Abra **Run > Edit Configurations...** e **duplique** a configuracao Application
   existente que ja conseguiu conectar ao Oracle da FIAP. Nome sugerido:
   `Oracle manual - inspecionar`. Preserve a configuracao original da API.
2. Na copia, mantenha integralmente **DB_URL**, **DB_USER**, **DB_PASS**, os perfis,
   os caminhos de arquivos de ambiente ja utilizados e o JDK 25. Nao copie valores
   de conexao para o codigo, argumentos, prints ou documentos.
3. Altere **Main class** para:

   ```text
   br.com.fiap.embarqueCertoAPI.maintenance.OracleDatabaseSetupManual
   ```

4. Selecione o modulo `embarqueCertoAPI` em **Use classpath of module**. A classe esta
   nas fontes de teste: a configuracao precisa incluir `target/test-classes` e as
   dependencias de teste. Se o seletor nao localizar a classe, use **Run** no icone
   ao lado do seu `main` para o IntelliJ gerar uma configuracao Application com o
   classpath de testes; transfira para ela a mesma configuracao de ambiente da
   execucao Oracle validada, sem divulgar os valores.
5. Defina **Working directory** como a raiz deste projeto, a pasta que contem
   `pom.xml` e `scripts`. Os caminhos dos scripts sao relativos a essa pasta.
6. Em **Program arguments**, use somente a acao abaixo, preservando argumentos de
   perfis Spring que ja sejam necessarios:

   ```text
   --acao=inspecionar
   ```

   Remova da copia argumentos de execucoes anteriores do diagnostico que nao sejam
   necessarios. `--diagnostico.oracle.enabled=true` nao ativa esta ferramenta nem
   autoriza escrita. Se o campo de argumentos nao aparecer, habilite-o em
   **Modify options > Program arguments**.
7. Clique em **Apply > Run** e procure `[Preparacao Oracle]` no console.

As alteracoes de codigo e os testes nao executam `ddl` nem `carga` no Oracle real.
Os testes automatizados usam essas acoes apenas contra mocks JDBC em memoria.

## Argumentos para a criacao manual posterior

Na configuracao Application da classe manual acima, mantenha as variaveis de
conexao existentes, o classpath de testes e a raiz do projeto como working directory.
Para a identidade informada (USER/SCHEMA RM562270, instancia cdb1, servico orcl,
PDB ORCL), os argumentos exatos para uma futura criacao manual sao:

```text
--acao=ddl --instancia-esperada=cdb1 --servico-esperado=orcl
```

Esses argumentos pertencem a `OracleDatabaseSetupManual`, nao a
`EmbarqueCertoApiApplication`. USER e CURRENT_SCHEMA devem ser RM562270; instancia
e servico devem corresponder exatamente aos argumentos. O PDB tambem aparece no
console para conferência. A identidade e lida novamente na propria conexao da
operacao; nao basta um resultado antigo de inspecao.

O DDL consulta somente a identidade, os nomes dos 11 objetos e dos indices que o
DDL criara, e possiveis colisoes dos 61 nomes de constraints. Se qualquer nome ja
estiver ocupado, mesmo em estrutura completa e compativel, a escrita fica bloqueada.
Erro, cancelamento, timeout ou resposta inconclusiva tambem interrompem a operacao
antes do primeiro comando DDL. Nenhuma checagem e ignorada para contornar timeout.

Depois de uma criacao manual bem-sucedida, executar novamente `--acao=inspecionar`
para conferir a estrutura. A carga continua sendo uma operacao separada; nao e
disparada pelo DDL. Nao repetir DDL apos falha parcial: ele tem commits implicitos e
os objetos persistidos bloquearao nova criacao ate revisao manual.

## O que a inspecao mostra

- Usuario conectado, CURRENT_SCHEMA, instancia, servico, banco e PDB.
- Objetos do usuario conectado com os nomes exatos das 11 tabelas e dos indices
  previstos, incluindo conflitos com views ou outros tipos de objeto.
- Colisoes dos nomes de constraints, inclusive se usados em outra tabela.
- Se as 11 tabelas estiverem presentes sem conflito preliminar, comparacao completa
  das 65 colunas e 61 constraints, seguida de contagens por OWNER explicito.

Com conjunto vazio ou parcial, nao consulta colunas, relacionamentos, triggers ou
contagens: esses detalhes nao sao necessarios para concluir VAZIO ou REVISAO.
O executor nao pesquisa mais tabelas de outros OWNERs em `ALL_TABLES`; isso continua
disponivel na rotina separada `OracleTableDiagnostic`, que foi preservada.

Estados:

| Estado | Significado |
| --- | --- |
| VAZIO | As consultas de nomes terminaram sem erro e nenhum objeto ou nome reservado foi encontrado; nao significa que o schema inteiro esteja vazio. |
| COMPATIVEL | As 11 tabelas e as definicoes esperadas correspondem aos metadados consultados. |
| REVISAO | Ha estrutura parcial, nomes conflitantes ou alguma incompatibilidade; escritas ficam bloqueadas. |

Falha de consulta nao gera VAZIO: a operacao termina como INCONCLUSIVA e a escrita
fica bloqueada. Respostas parciais de um cursor cancelado tambem nao sao aceitas.

Na comparacao entram nomes e tipos das colunas, precisao/escala/tamanho, valores
default, nullabilidade, PKs e unicas com a ordem de colunas, expressoes CHECK,
FKs com OWNER/tabela/colunas referenciadas e regras de exclusao. As constraints
devem estar ENABLED, VALIDATED e nao diferiveis. NOT NULL sem nome e conferido pela
nullabilidade das colunas. Triggers habilitadas nao previstas no DDL bloqueiam a
preparacao para evitar efeitos adicionais de uma futura carga.

As diferencas sao informadas por tabela ou constraint. A ferramenta nao tenta
corrigir estrutura, remover objetos ou alterar definicoes existentes.

Se o resultado for vazio, compare instancia, servico e PDB com o integrante que
criou as tabelas. O mesmo nome de usuario pode existir em bancos/servicos
diferentes. A ausencia no schema conectado nao e uma consulta global a todos os
bancos ou OWNERs e nao prova que o DDL nunca tenha sido executado.

## ORA-01013 e consultas de metadados

A versao anterior aplicava `setQueryTimeout(30)` a cada consulta, mas continuava
lendo colunas e constraints mesmo com zero tabelas, usando joins com
`ALL_CONSTRAINTS` e `ALL_CONS_COLUMNS`. O ORA-01013 e compativel com cancelamento
por timeout JDBC ou cancelamento solicitado. Sem um log por consulta daquela
execucao, nao e possivel afirmar qual instrucao demorou ou a causa exata no servidor.

A versao atual mantem o limite de 30 segundos e informa o rotulo de cada consulta,
seu inicio e o tempo ate conclusao ou falha. Uma nova ocorrencia identifica se o
cancelamento veio da identidade, USER_OBJECTS, colisoes de nomes, colunas ou outra
etapa, sem publicar credenciais ou mensagens brutas do driver.

Inspecao vazia e pre-verificacao de DDL usam tres SELECTs simples e delimitados por
nomes exatos, sem joins. Para a conferencia completa da carga, as FKs sao resolvidas
em memoria por R_OWNER e R_CONSTRAINT_NAME usando apenas `USER_CONSTRAINTS` e
`USER_CONS_COLUMNS` das 11 tabelas. Nenhum join com catalogos `ALL_*` e necessario.
Referencia externa, ausente ou diferente da prevista bloqueia a carga.

Referencias: [cancelamento e timeout no Oracle JDBC](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/JDBC-troubleshooting.html)
e [ORA-01013](https://docs.oracle.com/en/error-help/db/ora-01013/).

## Acoes independentes e protecoes para uso futuro

| Acao | Comportamento |
| --- | --- |
| `inspecionar` | Somente SELECT para inventario e comparacao. |
| `ddl` | Usa pre-verificacao objetiva e cria somente com identidade confirmada e todos os nomes previstos livres. Qualquer objeto existente bloqueia; nao tenta ajustar ou recriar. |
| `carga` | Repete a inspecao; exige estado COMPATIVEL e todas as 11 tabelas vazias. Executa somente os 132 INSERTs aprovados. |
| `validar` | Somente SELECT: exige estrutura COMPATIVEL, confere quantidades e executa as tres consultas do DQL. |

Uma execucao aceita exatamente uma acao. Sem `--acao`, mostra ajuda e encerra antes
de criar o contexto Spring ou abrir conexao. Nao existe modo que encadeie DDL e
carga automaticamente.

Antes de qualquer escrita futura, USER e CURRENT_SCHEMA precisam ser exatamente
`RM562270`. Tambem serao obrigatorios os argumentos `--instancia-esperada` e
`--servico-esperado`, com os valores exatos vistos na inspecao e confirmados com a
equipe. A ferramenta compara ambos com a sessao que executara a operacao. Esses
argumentos nao substituem nem alteram DB_URL, DB_USER ou DB_PASS.

Durante uma futura preparacao autorizada, a API e as demais sessoes da equipe nao
devem executar escritas ou DDL no projeto. A ferramenta e local e nao fornece um
mecanismo de coordenacao entre varios integrantes.

## Scripts preservados e copias locais

Os tres scripts originais de `scripts/` permanecem intactos.

- `scripts/local/01_DDL_EmbarqueCerto_sem_drop.sql`: preserva os 11 CREATE TABLE,
  17 ALTER TABLE para FKs e 11 COMMENT ON TABLE. Todo o bloco PL/SQL de limpeza e
  seu delimitador `/` foram removidos.
- `scripts/local/02_DML_EmbarqueCerto_sem_delete.sql`: preserva exatamente os 132
  INSERTs na ordem original. Os 11 DELETEs e o COMMIT do arquivo foram removidos.
- `scripts/03_DQL_EmbarqueCerto.sql`: continua sendo a fonte das tres consultas SELECT.

Somente esses caminhos fixos sao aceitos. Os arquivos sao lidos em UTF-8 e
verificados por SHA-256 do texto normalizado (aceita LF/CRLF e BOM inicial).
Uma mudanca no conteudo bloqueia a ferramenta antes da conexao; exige revisao do
arquivo e do hash aprovado no codigo. Nao basta acrescentar um comando ao arquivo
para que ele passe a ser executado. Os SQLs executados usam os textos verificados
em memoria, sem reler um arquivo que possa ter sido alterado depois da verificacao.

O executor usa o `ScriptUtils` JDBC ja incluido nas dependencias Spring, com
separador `;` e interrupcao no primeiro erro. Nao envia o arquivo inteiro como uma
unica instrucao JDBC e nao usa separacao ingênua por ponto e virgula. Por exemplo,
um `;` dentro de um comentario de tabela entre aspas continua sendo parte do texto.

## Ordem e quantidades da carga

As tabelas sao criadas antes das FKs. A carga segue a ordem original, dos pais para
os filhos; nao desabilita constraints nem cria sequences, identity ou tabelas extras.

| Tabela | Registros |
| --- | ---: |
| T_EC_OPERADORA | 5 |
| T_EC_LINHA | 6 |
| T_EC_ESTACAO | 15 |
| T_EC_LINHA_ESTACAO | 22 |
| T_EC_USUARIO | 8 |
| T_EC_TIPO_OCORRENCIA | 6 |
| T_EC_OCORRENCIA | 12 |
| T_EC_CONFIRMACAO | 17 |
| T_EC_REGISTRO_LOTACAO | 20 |
| T_EC_TRAJETO | 8 |
| T_EC_ALERTA | 13 |
| **Total** | **132** |

## Transacoes, falhas parciais e reexecucao

DDL Oracle tem commits implicitos. Uma falha pode deixar parte das tabelas ou
constraints persistida; a ferramenta nao promete rollback da estrutura. Ela para,
informa o arquivo/instrucao e tenta apresentar o estado restante. Uma nova tentativa
em estrutura parcial fica bloqueada ate revisao manual. Nao existem DROP, DELETE,
TRUNCATE ou reparos automaticos para "limpar e tentar de novo".

A carga usa uma conexao dedicada com `autoCommit=false`. Antes de confirmar, confere
as quantidades de cada tabela e o total de 132. Erro de INSERT ou divergencia causa
rollback explicito da transacao. PKs e unicas continuam ativas; duplicidade nao e
ignorada nem convertida em UPDATE/MERGE. Uma nova carga com qualquer dado existente
e bloqueada antes do primeiro INSERT.

O COMMIT e feito somente pelo Java. Depois, as quantidades sao conferidas por uma
nova conexao. Perda de conexao durante o COMMIT e relatada como resultado
INDETERMINADO; falha depois de um COMMIT confirmado e relatada sem alegar rollback.
Em ambos os casos, executar somente inspecao antes de qualquer nova tentativa.

Erros exibem codigo Oracle e SQLState; mensagens brutas do driver e credenciais
nao sao reproduzidas pela ferramenta. Exemplos de causas identificadas incluem
permissao insuficiente, quota de tablespace, objeto ja existente e chave duplicada.

## Validacao automatizada e remocao

O comando de testes seleciona apenas as classes que usam JDBC simulado, evitando
o teste existente `contextLoads`, que depende da configuracao real do banco:

```text
mvn -Dtest=OracleDatabaseSetupManualTests,OracleTableDiagnosticTests test
```

Esses testes validam preservacao dos scripts, integridade, bloqueios de destino e
estrutura, fluxo DDL parcial, carga atomica, contagens, falhas no COMMIT/rollback e
validacao somente de leitura. Tambem cobrem o caminho curto de tres consultas,
ORA-01013 em cada etapa, SQLTimeoutException, cancelamento durante a leitura de
resultados, metadados inconclusivos e colisoes de nomes. Nao se conectam ao Oracle FIAP. Os identificadores
INSTANCE_TEST, SERVICE_TEST e outros valores dos testes sao ficticios.

A execucao normal de `EmbarqueCertoApiApplication` continua independente. Para
desativar a ferramenta, basta nao executar sua configuracao Application. Para
remove-la definitivamente, excluir as duas classes de `maintenance` em
`src/test/java`, as duas copias de `scripts/local` e este documento. Preservar os
scripts originais e a rotina de diagnostico ja existente.
