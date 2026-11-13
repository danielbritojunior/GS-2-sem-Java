# Verificacao local e temporaria das tabelas Oracle

A classe `OracleTableDiagnostic` usa o `DataSource` que a aplicacao ja recebe do Spring.
Ela roda uma vez na inicializacao, somente quando `diagnostico.oracle.enabled=true`.
Nao existe endpoint para essa verificacao.

## Executar no IntelliJ

1. Pare a aplicacao, se estiver em execucao.
2. Abra **Run > Edit Configurations...** e selecione a mesma configuracao que ja
   conseguiu se conectar ao Oracle FIAP.
3. Preserve as variaveis **DB_URL**, **DB_USER** e **DB_PASS**, os perfis e as demais
   opcoes dessa configuracao. Nao copie credenciais para o codigo ou para argumentos.
4. Em **Program arguments**, acrescente, preservando os argumentos existentes:

   ```text
   --diagnostico.oracle.enabled=true
   ```

   Se o campo nao estiver visivel, habilite-o em **Modify options > Program arguments**.
5. Execute essa mesma configuracao com **Run** e procure no console por
   `[Diagnostico Oracle]`.

Nao e necessario editar `application.properties`, `pom.xml` ou qualquer configuracao
de acesso ao Oracle.

## Consultas e resultados

A rotina usa a mesma conexao JDBC para identificar a sessao e consultar os dois
catalogos. Primeiro, mostra o usuario conectado e o schema usado para resolver
nomes de objetos sem OWNER:

```sql
SELECT USER AS usuario,
       SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS schema_atual
FROM dual
```

Depois, preserva a consulta de tabelas do usuario conectado:

```sql
SELECT table_name FROM user_tables WHERE table_name LIKE 'T_EC_%' ORDER BY table_name
```

E consulta as tabelas visiveis para essa sessao, incluindo outros proprietarios:

```sql
SELECT owner, table_name
FROM all_tables
WHERE table_name LIKE 'T_EC_%'
ORDER BY owner, table_name
```

O console mostra `USER`, `CURRENT_SCHEMA`, cada nome de `USER_TABLES` e cada par
`OWNER`/`TABLE_NAME` de `ALL_TABLES`, com os respectivos totais. `USER_TABLES` descreve
tabelas pertencentes ao usuario conectado; `ALL_TABLES` inclui tabelas acessiveis a
ele em outros schemas. Veja a [referencia Oracle sobre ALL_TABLES e USER_TABLES](https://docs.oracle.com/en/database/oracle/oracle-database/18/refrn/ALL_TABLES.html).

Para cada uma das 11 tabelas autorizadas, a comparacao informa se consta em
`USER_TABLES` e quais OWNERs aparecem em `ALL_TABLES`. Uma consulta que falha fica
`INDETERMINADA`; ela nao e interpretada como resultado zero. Se a consulta de
`USER_TABLES` falhar, a verificacao de `ALL_TABLES` continua, e vice-versa.

Se `CURRENT_SCHEMA` for diferente de `USER`, as contagens de `USER_TABLES` usam
`"USER"."TABELA_AUTORIZADA"` explicitamente para preservar a correspondencia entre
o catalogo e a tabela contada. Se a identidade da sessao nao puder ser obtida, essas
contagens sao ignoradas, mas os catalogos e os testes de SELECT por OWNER continuam.

## Confirmacao de leitura e investigacao da divergencia

Para distinguir visibilidade no catalogo de permissao de leitura, cada par
OWNER/tabela autorizada encontrado em `ALL_TABLES` recebe uma consulta SELECT sem
retornar registros:

```sql
SELECT 1 FROM "OWNER"."TABELA_AUTORIZADA" WHERE 1 = 0
```

O nome da tabela deve pertencer a lista fixa; o OWNER vem do catalogo e e escapado
como identificador entre aspas. Nomes fora da lista fixa nao recebem consultas de
leitura nem contagens. Nenhum nome de OWNER e usado como SQL sem esse escape.

O resumo mostra quantos dos 11 nomes estao visiveis, quantos tiveram SELECT
confirmado e quantas tabelas foram confirmadas por OWNER. Um mesmo nome encontrado
em dois OWNERs conta uma vez no total de nomes. Se os 11 nomes estiverem espalhados
entre schemas e nenhum OWNER tiver os 11, isso recebe aviso explicito. O resumo
tambem destaca o resultado para `CURRENT_SCHEMA`.

Um SELECT qualificado bem-sucedido confirma acesso como `OWNER.TABELA`. Isso nao
comprova que o mesmo nome sem OWNER sera resolvido pela aplicacao: sinonimos nao
sao investigados, e o diagnostico nao altera `CURRENT_SCHEMA`.

Interpretacao dos resultados:

- `USER_TABLES=0` e tabelas em `ALL_TABLES` com outro OWNER: a sessao ve objetos de
  outro proprietario. Confira o OWNER informado e os testes de SELECT.
- `USER` diferente do usuario esperado ou `CURRENT_SCHEMA` diferente de `USER`:
  existe uma diferenca de identidade/resolucao de nomes a investigar na configuracao
  ja existente, sem alterar credenciais durante o diagnostico.
- Ambos os catalogos vazios: nenhuma tabela correspondente esta visivel nessa
  sessao. Isso **nao prova que o DDL nao foi executado**.
- Falha JDBC: o resultado do catalogo afetado fica desconhecido; confira o codigo
  Oracle e o SQLState antes de concluir que uma tabela esta ausente.

Possiveis causas de catalogos vazios, ainda nao confirmadas: conexoes apontando
para servicos, instancias ou PDBs diferentes apesar do mesmo nome de usuario;
objetos em outro OWNER sem visibilidade/permissao; nomes criados entre aspas com
caixa diferente; objetos que sejam visoes/sinonimos em vez de tabelas; alteracoes
posteriores. Compare esses pontos com o integrante que criou as tabelas sem
compartilhar senhas ou publicar valores de conexao.

## Contagens e comparacao com o DML

As contagens usam `SELECT COUNT(*) FROM <tabela>` somente para nomes da constante
`EXPECTED_TABLES` que tambem tenham sido encontrados na primeira consulta. Nomes
retornados pelo banco nunca sao usados diretamente para montar essas consultas.
Tabelas fora da lista fixa sao exibidas, mas sua contagem e ignorada.

A lista oficial e as quantidades esperadas foram informadas a partir de
`01_DDL_EmbarqueCerto.sql` e `02_DML_EmbarqueCerto.sql`. Esses scripts nao sao
executados pelo diagnostico.

| Tabela autorizada | Registros esperados do DML |
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

Para cada tabela autorizada encontrada, o console mostra `encontrado`, `esperado`
e `[OK]` ou `[DIVERGENCIA]`. Uma tabela ausente recebe aviso e nao e consultada com
`COUNT(*)`. Uma tabela existente e vazia retorna zero e e comparada normalmente.

O resumo informa quantas das 11 tabelas autorizadas estao presentes e ausentes,
quantas contagens terminaram e quantas divergiram. O total das 11 tabelas so e
mostrado quando todas as contagens terminam com sucesso; caso contrario, a rotina
informa `Comparacao total incompleta` e mostra apenas o subtotal das contagens
bem-sucedidas, junto do total esperado de 132. Erro ou ausencia nao vira contagem zero.
Mesmo que o total seja 132, confira as divergencias individuais: quantidades podem
se compensar entre tabelas. Contagens iguais nao verificam o conteudo dos registros.

Falhas JDBC mostram a operacao, o codigo Oracle, o SQLState e a mensagem. Os erros
ORA-01031 e ORA-00942 recebem explicacao adicional sobre permissoes/ausencia da tabela.
Uma falha de contagem nao impede a tentativa nas demais tabelas autorizadas. A rotina
nao registra as variaveis de conexao e nao executa DDL ou DML.

## Desativar e remover

Remova `--diagnostico.oracle.enabled=true` de **Program arguments**, ou substitua por
`--diagnostico.oracle.enabled=false`, e reinicie a aplicacao. Sem a propriedade com
valor `true`, o componente nao e criado nem realiza consultas.

Para remover definitivamente o diagnostico temporario, exclua apenas:

- `src/main/java/br/com/fiap/embarqueCertoAPI/diagnostic/OracleTableDiagnostic.java`
- `src/test/java/br/com/fiap/embarqueCertoAPI/diagnostic/OracleTableDiagnosticTests.java`
- `DIAGNOSTICO-ORACLE.md`

## Validacao automatizada isolada

Os testes usam JDBC simulado e nao se conectam ao Oracle. Execute somente a classe
de teste do diagnostico para evitar o teste existente de inicializacao da aplicacao:

```text
mvn -Dtest=OracleTableDiagnosticTests test
```

Isso valida o acionamento pela propriedade, as 11 tabelas autorizadas e os 132
registros esperados, tabelas ausentes, tabelas extras sem autorizacao, contagens
iguais/divergentes/zeradas, total incompleto e a continuidade apos erro de acesso.
Tambem valida a identidade da sessao, OWNERs externos, nomes distribuidos entre
schemas, falhas independentes dos catalogos, SELECT negado apesar da visibilidade,
escape de OWNER e contagens quando `CURRENT_SCHEMA` difere de `USER`. Os erros de
permissao e de conexao tambem sao simulados.

Compilar ou executar esses testes nao consulta o Oracle real e nao comprova que as
tabelas existem no schema FIAP. A existencia real e as quantidades atuais so serao
confirmadas ao executar no IntelliJ com a configuracao de conexao ja validada.
