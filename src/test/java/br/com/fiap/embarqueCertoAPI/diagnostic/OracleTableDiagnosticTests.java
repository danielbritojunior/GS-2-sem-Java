package br.com.fiap.embarqueCertoAPI.diagnostic;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OracleTableDiagnosticTests {

	private static final String TABLES_QUERY =
			"SELECT table_name FROM user_tables WHERE table_name LIKE 'T_EC_%' ORDER BY table_name";

	private static final String SESSION_QUERY = "SELECT USER AS usuario, "
			+ "SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS schema_atual FROM dual";

	private static final String ALL_TABLES_QUERY = "SELECT owner, table_name FROM all_tables "
			+ "WHERE table_name LIKE 'T_EC_%' ORDER BY owner, table_name";

	private static final String SESSION_USER = "RM562270";

	private record OwnedTable(String owner, String name) {
	}

	private static final Map<String, Long> EXPECTED_COUNTS = Map.ofEntries(
			Map.entry("T_EC_OPERADORA", 5L),
			Map.entry("T_EC_LINHA", 6L),
			Map.entry("T_EC_ESTACAO", 15L),
			Map.entry("T_EC_LINHA_ESTACAO", 22L),
			Map.entry("T_EC_USUARIO", 8L),
			Map.entry("T_EC_TIPO_OCORRENCIA", 6L),
			Map.entry("T_EC_OCORRENCIA", 12L),
			Map.entry("T_EC_CONFIRMACAO", 17L),
			Map.entry("T_EC_REGISTRO_LOTACAO", 20L),
			Map.entry("T_EC_TRAJETO", 8L),
			Map.entry("T_EC_ALERTA", 13L));

	private final DataSource dataSource = mock(DataSource.class);
	private final Connection connection = mock(Connection.class);
	private final Statement statement = mock(Statement.class);
	private final ResultSet result = mock(ResultSet.class);
	private final List<String> expectedAccessQueries = new ArrayList<>();

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(DataSource.class, () -> dataSource)
			.withUserConfiguration(OracleTableDiagnostic.class);

	@Test
	void staysDisabledWithoutExplicitTrueProperty() {
		contextRunner.run(context -> assertThat(context).doesNotHaveBean(OracleTableDiagnostic.class));
		contextRunner.withPropertyValues("diagnostico.oracle.enabled=false")
				.run(context -> assertThat(context).doesNotHaveBean(OracleTableDiagnostic.class));
		contextRunner.withPropertyValues("diagnostico.oracle.enabled=1")
				.run(context -> assertThat(context).doesNotHaveBean(OracleTableDiagnostic.class));
		verifyNoInteractions(dataSource);
	}

	@Test
	void listsTablesAndSkipsCountsOutsideFixedList(CapturedOutput output) throws SQLException {
		prepareTables("T_EC_FORA_DA_LISTA", "T_EC_OUTRA_FORA_DA_LISTA");

		contextRunner.withPropertyValues("diagnostico.oracle.enabled=true").run(context -> {
			assertThat(context).hasSingleBean(OracleTableDiagnostic.class);
			context.getBean(OracleTableDiagnostic.class).run(new DefaultApplicationArguments());
		});

		assertThat(output.getOut()).contains("Tabela encontrada: T_EC_FORA_DA_LISTA",
				"Tabela encontrada: T_EC_OUTRA_FORA_DA_LISTA", "Total de tabelas encontradas: 2",
				"T_EC_FORA_DA_LISTA esta fora da lista fixa; contagem ignorada.");
		verifyOnlyListingQuery();
	}

	@Test
	void reportsZeroTablesWithoutTryingCounts(CapturedOutput output) throws SQLException {
		prepareTables();

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Total de tabelas encontradas: 0",
				"Tabelas autorizadas: presentes=0, ausentes=11, total esperado=11.",
				"Comparacao total incompleta: contagens concluidas=0/11, subtotal encontrado=0, "
						+ "total esperado no DML=132.",
				"Nenhuma tabela T_EC_% encontrada em ALL_TABLES nesta sessao.",
				"Comparacao dos catalogos: USER_TABLES=0, ALL_TABLES=0.",
				"Possiveis causas, nao confirmadas", "servico/instancia/PDB diferente",
				"Ausencia nesses catalogos nao prova que o DDL nao foi executado.");
		for (var table : EXPECTED_COUNTS.keySet()) {
			assertThat(output.getAll()).contains("Tabela esperada nao encontrada em USER_TABLES: " + table);
		}
		verifyOnlyListingQuery();
	}

	@Test
	void countsAllElevenAuthorizedTablesAndComparesWithDml(CapturedOutput output) throws SQLException {
		prepareTables(EXPECTED_COUNTS.keySet().toArray(String[]::new));
		var expectedQueries = new ArrayList<String>();
		expectedQueries.add(TABLES_QUERY);
		for (var expected : EXPECTED_COUNTS.entrySet()) {
			prepareCount(expected.getKey(), expected.getValue());
			expectedQueries.add("SELECT COUNT(*) FROM " + expected.getKey());
		}

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		for (var expected : EXPECTED_COUNTS.entrySet()) {
			assertThat(output.getAll()).contains(expected.getKey() + ": encontrado=" + expected.getValue()
					+ ", esperado=" + expected.getValue() + " [OK].");
		}
		assertThat(output.getAll()).contains("Total de tabelas encontradas: 11",
				"Tabelas autorizadas: presentes=11, ausentes=0, total esperado=11.",
				"Contagens concluidas=11, divergentes=0.",
				"Total de registros das 11 tabelas: encontrado=132, esperado no DML=132.",
				"ALL_TABLES: nomes autorizados visiveis=11/11; nomes com SELECT confirmado=11/11.",
				"OWNER=RM562270: tabelas autorizadas com SELECT confirmado=11/11.")
				.doesNotContain("[DIVERGENCIA]", "Comparacao total incompleta", "Tabela esperada nao encontrada");
		verifyQueries(expectedQueries.toArray(String[]::new));
	}

	@Test
	void countsOnlyExistingAuthorizedTablesAndReportsMissingAndDivergentOnes(CapturedOutput output)
			throws SQLException {
		prepareTables("T_EC_OPERADORA", "T_EC_USUARIO", "T_EC_ALERTA", "T_EC_NAO_AUTORIZADA");
		prepareCount("T_EC_OPERADORA", 5);
		prepareCount("T_EC_USUARIO", 0);
		prepareCount("T_EC_ALERTA", 14);

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("T_EC_OPERADORA: encontrado=5, esperado=5 [OK].",
				"T_EC_USUARIO: encontrado=0, esperado=8 [DIVERGENCIA].",
				"T_EC_ALERTA: encontrado=14, esperado=13 [DIVERGENCIA].",
				"T_EC_NAO_AUTORIZADA esta fora da lista fixa; contagem ignorada.",
				"Tabela esperada nao encontrada em USER_TABLES: T_EC_LINHA",
				"Total de tabelas encontradas: 4",
				"Tabelas autorizadas: presentes=3, ausentes=8, total esperado=11.",
				"Contagens concluidas=3, divergentes=2.",
				"Comparacao total incompleta: contagens concluidas=3/11, subtotal encontrado=19, "
						+ "total esperado no DML=132.")
				.doesNotContain("Total de registros das 11 tabelas:");
		verifyQueries(TABLES_QUERY, "SELECT COUNT(*) FROM T_EC_OPERADORA", "SELECT COUNT(*) FROM T_EC_USUARIO",
				"SELECT COUNT(*) FROM T_EC_ALERTA");
	}

	@Test
	void continuesOtherCountsAfterAccessErrorAndKeepsTotalIncomplete(CapturedOutput output) throws SQLException {
		prepareTables("T_EC_OPERADORA", "T_EC_LINHA");
		when(statement.executeQuery("SELECT COUNT(*) FROM T_EC_OPERADORA"))
				.thenThrow(new SQLException("ORA-00942: table or view does not exist", "42000", 942));
		prepareCount("T_EC_LINHA", 6);

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Falha ao contar registros de T_EC_OPERADORA",
				"Codigo Oracle=942", "SQLState=42000", "sem permissao SELECT (ORA-00942)",
				"T_EC_LINHA: encontrado=6, esperado=6 [OK].",
				"Tabelas autorizadas: presentes=2, ausentes=9, total esperado=11.",
				"Comparacao total incompleta: contagens concluidas=1/11, subtotal encontrado=6, "
						+ "total esperado no DML=132.")
				.doesNotContain("T_EC_OPERADORA: encontrado=0", "Total de registros das 11 tabelas:");
		verifyQueries(TABLES_QUERY, "SELECT COUNT(*) FROM T_EC_OPERADORA", "SELECT COUNT(*) FROM T_EC_LINHA");
	}

	@Test
	void reportsPermissionErrorWithOracleCodeAndSqlState(CapturedOutput output) throws SQLException {
		prepareConnection();
		when(statement.executeQuery(TABLES_QUERY))
				.thenThrow(new SQLException("ORA-01031: insufficient privileges", "42000", 1031));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Codigo Oracle=1031", "SQLState=42000",
				"Permissao insuficiente", "ORA-01031")
				.doesNotContain("Total de tabelas encontradas: 0", "Tabela esperada nao encontrada");
		verifyOnlyListingQuery();
	}

	@Test
	void reportsConnectionFailureWithoutIssuingSql(CapturedOutput output) throws SQLException {
		when(dataSource.getConnection())
				.thenThrow(new SQLException("ORA-01017: invalid credential", "72000", 1017));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Codigo Oracle=1017", "SQLState=72000", "ORA-01017");
		verifyNoInteractions(connection, statement);
	}

	@Test
	void findsAllElevenTablesInAnotherOwnerWhileUserTablesIsEmpty(CapturedOutput output) throws SQLException {
		prepareTables();
		prepareAllTables(EXPECTED_COUNTS.keySet().stream()
				.map(name -> new OwnedTable("OUTRO_OWNER", name)).toArray(OwnedTable[]::new));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Sessao: USER=RM562270, CURRENT_SCHEMA=RM562270.",
				"ALL_TABLES: OWNER=OUTRO_OWNER, TABLE_NAME=T_EC_OPERADORA.",
				"Comparacao T_EC_OPERADORA: USER_TABLES=AUSENTE, ALL_TABLES OWNER(s)=[OUTRO_OWNER].",
				"ALL_TABLES: nomes autorizados visiveis=11/11; nomes com SELECT confirmado=11/11.",
				"OWNER=OUTRO_OWNER: tabelas autorizadas com SELECT confirmado=11/11.",
				"Os 11 nomes autorizados estao acessiveis por SELECT com OWNER explicito.",
				"CURRENT_SCHEMA=RM562270: SELECT por OWNER explicito confirmado para 0/11 tabelas.");
		verifyOnlyListingQuery();
	}

	@Test
	void doesNotConfuseVisibilityWithSelectPermission(CapturedOutput output) throws SQLException {
		prepareTables();
		prepareAllTables(new OwnedTable("OUTRO_OWNER", "T_EC_OPERADORA"),
				new OwnedTable("OUTRO_OWNER", "T_EC_NAO_AUTORIZADA"));
		when(statement.executeQuery("SELECT 1 FROM \"OUTRO_OWNER\".\"T_EC_OPERADORA\" WHERE 1 = 0"))
				.thenThrow(new SQLException("ORA-01031: insufficient privileges", "42000", 1031));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Falha ao verificar SELECT de OUTRO_OWNER.T_EC_OPERADORA",
				"Codigo Oracle=1031", "SQLState=42000",
				"ALL_TABLES: nomes autorizados visiveis=1/11; nomes com SELECT confirmado=0/11.",
				"Acesso SELECT ao conjunto de 11 tabelas NAO TOTALMENTE CONFIRMADO.")
				.doesNotContain("Os 11 nomes autorizados estao acessiveis");
		verifyOnlyListingQuery();
	}

	@Test
	void doesNotMergeDifferentOwnersIntoOneCompleteSchema(CapturedOutput output) throws SQLException {
		prepareTables();
		var index = new AtomicInteger();
		prepareAllTables(EXPECTED_COUNTS.keySet().stream()
				.map(name -> new OwnedTable(index.getAndIncrement() < 5 ? "OWNER_A" : "OWNER_B", name))
				.toArray(OwnedTable[]::new));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("nomes com SELECT confirmado=11/11",
				"OWNER=OWNER_A: tabelas autorizadas com SELECT confirmado=5/11.",
				"OWNER=OWNER_B: tabelas autorizadas com SELECT confirmado=6/11.",
				"nenhum OWNER teve o conjunto completo de 11 tabelas confirmado.");
		verifyOnlyListingQuery();
	}

	@Test
	void stillChecksAllTablesWhenUserTablesFails(CapturedOutput output) throws SQLException {
		prepareTables();
		when(statement.executeQuery(TABLES_QUERY))
				.thenThrow(new SQLException("ORA-01031: insufficient privileges", "42000", 1031));
		prepareAllTables(new OwnedTable("OUTRO_OWNER", "T_EC_OPERADORA"));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Falha ao consultar USER_TABLES",
				"Comparacao T_EC_OPERADORA: USER_TABLES=INDETERMINADO, ALL_TABLES OWNER(s)=[OUTRO_OWNER].",
				"SELECT confirmado: OWNER=OUTRO_OWNER, TABLE_NAME=T_EC_OPERADORA.")
				.doesNotContain("Total de tabelas encontradas: 0", "USER_TABLES=AUSENTE");
		verifyOnlyListingQuery();
	}

	@Test
	void keepsOwnedCountsWhenAllTablesFailsWithoutReportingZero(CapturedOutput output) throws SQLException {
		prepareTables("T_EC_OPERADORA");
		prepareCount("T_EC_OPERADORA", 5);
		when(statement.executeQuery(ALL_TABLES_QUERY))
				.thenThrow(new SQLException("ORA-01031: insufficient privileges", "42000", 1031));
		expectedAccessQueries.clear();

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Falha ao consultar ALL_TABLES",
				"T_EC_OPERADORA: encontrado=5, esperado=5 [OK].", "Acessibilidade das 11 tabelas via esse catalogo INDETERMINADA.")
				.doesNotContain("Total de objetos encontrados em ALL_TABLES: 0", "nomes com SELECT confirmado=0/11",
						"Nenhuma tabela T_EC_% encontrada em ALL_TABLES");
		verifyQueries(TABLES_QUERY, "SELECT COUNT(*) FROM T_EC_OPERADORA");
	}

	@Test
	void qualifiesOwnedCountsWhenCurrentSchemaDiffersFromUser(CapturedOutput output) throws SQLException {
		prepareTables("T_EC_OPERADORA");
		prepareSession(SESSION_USER, "OUTRO_OWNER");
		prepareCount("\"RM562270\".\"T_EC_OPERADORA\"", 5);

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Sessao: USER=RM562270, CURRENT_SCHEMA=OUTRO_OWNER.",
				"CURRENT_SCHEMA difere de USER", "T_EC_OPERADORA: encontrado=5, esperado=5 [OK].");
		verifyQueries(TABLES_QUERY, "SELECT COUNT(*) FROM \"RM562270\".\"T_EC_OPERADORA\"");
	}

	@Test
	void keepsCatalogChecksButSkipsOwnedCountsWhenSessionIdentityFails(CapturedOutput output) throws SQLException {
		prepareTables("T_EC_OPERADORA");
		when(statement.executeQuery(SESSION_QUERY))
				.thenThrow(new SQLException("ORA-01031: insufficient privileges", "42000", 1031));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("Falha ao identificar USER e CURRENT_SCHEMA",
				"Identidade da sessao indisponivel; contagens de USER_TABLES nao executadas",
				"SELECT confirmado: OWNER=RM562270, TABLE_NAME=T_EC_OPERADORA.")
				.doesNotContain("T_EC_OPERADORA: encontrado=");
		verifyOnlyListingQuery();
	}

	@Test
	void quotesOwnerAndNeverSelectsAnUnauthorizedTable(CapturedOutput output) throws SQLException {
		prepareTables();
		prepareAllTables(new OwnedTable("OUTRO\"OWNER", "T_EC_OPERADORA"),
				new OwnedTable("OUTRO\"OWNER", "T_EC_OPERADORA_EXTRA"));

		new OracleTableDiagnostic(dataSource).run(new DefaultApplicationArguments());

		assertThat(output.getAll()).contains("SELECT confirmado: OWNER=OUTRO\"OWNER, TABLE_NAME=T_EC_OPERADORA.");
		verify(statement).executeQuery("SELECT 1 FROM \"OUTRO\"\"OWNER\".\"T_EC_OPERADORA\" WHERE 1 = 0");
		verifyOnlyListingQuery();
	}

	private void prepareConnection() throws SQLException {
		when(dataSource.getConnection()).thenReturn(connection);
		when(connection.createStatement()).thenReturn(statement);
		when(statement.executeQuery(TABLES_QUERY)).thenReturn(result);
		prepareSession(SESSION_USER, SESSION_USER);
		prepareAllTables();
	}

	private void prepareSession(String user, String schema) throws SQLException {
		var identity = mock(ResultSet.class);
		when(identity.next()).thenReturn(true);
		when(identity.getString("USUARIO")).thenReturn(user);
		when(identity.getString("SCHEMA_ATUAL")).thenReturn(schema);
		when(statement.executeQuery(SESSION_QUERY)).thenReturn(identity);
	}

	private void prepareAllTables(OwnedTable... tables) throws SQLException {
		var allResult = mock(ResultSet.class);
		var index = new AtomicInteger(-1);
		when(allResult.next()).thenAnswer(invocation -> index.incrementAndGet() < tables.length);
		when(allResult.getString("OWNER")).thenAnswer(invocation -> tables[index.get()].owner());
		when(allResult.getString("TABLE_NAME")).thenAnswer(invocation -> tables[index.get()].name());
		when(statement.executeQuery(ALL_TABLES_QUERY)).thenReturn(allResult);
		expectedAccessQueries.clear();
		for (var table : tables) {
			if (EXPECTED_COUNTS.containsKey(table.name())) {
				String query = "SELECT 1 FROM \"" + table.owner().replace("\"", "\"\"")
						+ "\".\"" + table.name() + "\" WHERE 1 = 0";
				expectedAccessQueries.add(query);
				when(statement.executeQuery(query)).thenReturn(mock(ResultSet.class));
			}
		}
	}

	private void prepareTables(String... tables) throws SQLException {
		prepareConnection();
		var index = new AtomicInteger(-1);
		when(result.next()).thenAnswer(invocation -> index.incrementAndGet() < tables.length);
		when(result.getString("TABLE_NAME")).thenAnswer(invocation -> tables[index.get()]);
		prepareAllTables(List.of(tables).stream().map(name -> new OwnedTable(SESSION_USER, name))
				.toArray(OwnedTable[]::new));
	}

	private void prepareCount(String table, long count) throws SQLException {
		var countResult = mock(ResultSet.class);
		when(countResult.next()).thenReturn(true);
		when(countResult.getLong(1)).thenReturn(count);
		when(statement.executeQuery("SELECT COUNT(*) FROM " + table)).thenReturn(countResult);
	}

	private void verifyOnlyListingQuery() throws SQLException {
		verifyQueries(TABLES_QUERY);
	}

	private void verifyQueries(String... expectedQueries) throws SQLException {
		var allExpectedQueries = new ArrayList<>(List.of(expectedQueries));
		allExpectedQueries.add(SESSION_QUERY);
		allExpectedQueries.add(ALL_TABLES_QUERY);
		allExpectedQueries.addAll(expectedAccessQueries);
		var queries = ArgumentCaptor.forClass(String.class);
		verify(statement, times(allExpectedQueries.size())).setQueryTimeout(30);
		verify(statement, times(allExpectedQueries.size())).executeQuery(queries.capture());
		assertThat(queries.getAllValues()).containsExactlyInAnyOrderElementsOf(allExpectedQueries);
		verify(statement, times(allExpectedQueries.size())).close();
		verifyNoMoreInteractions(statement);
		verify(connection).close();
	}

}
