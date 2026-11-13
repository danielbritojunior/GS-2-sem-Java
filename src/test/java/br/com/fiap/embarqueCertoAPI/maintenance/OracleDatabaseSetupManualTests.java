package br.com.fiap.embarqueCertoAPI.maintenance;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static br.com.fiap.embarqueCertoAPI.maintenance.OracleDatabaseSetupManual.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Todos os comandos de escrita deste teste sao recebidos somente por mocks JDBC. */
class OracleDatabaseSetupManualTests {

	private Bundle bundle;
	private Schema schema;
	private FakeOracle oracle;
	private OracleDatabaseSetupManual tool;
	private ByteArrayOutputStream console;

	@BeforeEach
	void prepare() throws Exception {
		bundle = Bundle.load(Path.of(""));
		schema = Schema.parse(bundle.ddl());
		oracle = new FakeOracle(schema, bundle.dql());
		console = new ByteArrayOutputStream();
		tool = new OracleDatabaseSetupManual(oracle.dataSource, bundle, new PrintStream(console, true, StandardCharsets.UTF_8));
	}

	@Test
	void noActionOpensNoContextOrConnection() {
		assertThat(Request.parse()).isNull();
		OracleDatabaseSetupManual.main(new String[0]);
		verifyNoInteractions(oracle.dataSource);
	}

	@Test
	void dataSourceOnlyContextDoesNotLoadApiJpaOrRunners() {
		new ApplicationContextRunner().withBean(DataSource.class, () -> oracle.dataSource)
				.withUserConfiguration(DataSourceOnlyConfiguration.class).run(context -> {
					assertThat(context).hasSingleBean(DataSource.class).doesNotHaveBean("entityManagerFactory");
					assertThat(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class)).isEmpty();
					assertThat(context.getBeansOfType(br.com.fiap.embarqueCertoAPI.controller.HealthController.class)).isEmpty();
				});
		verifyNoInteractions(oracle.dataSource);
	}

	@Test
	void rejectsMissingTargetAndCombinedOrRepeatedActionsBeforeConnecting() {
		for (String action : List.of("ddl", "carga")) {
			assertThatThrownBy(() -> Request.parse("--acao=" + action)).hasMessageContaining("Escritas exigem");
		}
		assertThatThrownBy(() -> Request.parse("--acao=ddl,carga")).hasMessageContaining("Acao invalida");
		assertThatThrownBy(() -> Request.parse("--acao=inspecionar", "--acao=ddl")).hasMessageContaining("exatamente uma");
		verifyNoInteractions(oracle.dataSource);
	}

	@Test
	void preservesEveryApprovedDdlStatementAndEveryOriginalInsert() throws Exception {
		String originalDdl = Files.readString(Path.of("scripts/01_DDL_EmbarqueCerto.sql"));
		String originalDml = Files.readString(Path.of("scripts/02_DML_EmbarqueCerto.sql"));
		var ddlStart = Pattern.compile("(?m)^CREATE TABLE").matcher(originalDdl);
		assertThat(ddlStart.find()).isTrue();
		var originalStatements = splitTopLevel(withoutComments(originalDdl.substring(ddlStart.start())), ';');
		var localStatements = splitTopLevel(withoutComments(bundle.ddl()), ';');
		assertThat(localStatements).containsExactlyElementsOf(originalStatements).hasSize(39);
		assertThat(localStatements.stream().filter(sql -> sql.startsWith("CREATE TABLE"))).hasSize(11);
		assertThat(localStatements.stream().filter(sql -> sql.startsWith("ALTER TABLE"))).hasSize(17);
		assertThat(localStatements.stream().filter(sql -> sql.startsWith("COMMENT ON TABLE"))).hasSize(11);
		String originalInserts = withoutComments(originalDml).replaceAll("(?im)^DELETE FROM[^;]+;", "")
				.replaceAll("(?im)^COMMIT;", "");
		var inserts = splitTopLevel(withoutComments(bundle.dml()), ';');
		assertThat(inserts).containsExactlyElementsOf(splitTopLevel(originalInserts, ';')).hasSize(132);
		assertThat(inserts).allMatch(sql -> sql.startsWith("INSERT INTO t_ec_"));
		for (var expected : EXPECTED_ROWS.entrySet()) {
			assertThat(inserts.stream().filter(sql -> sql.toUpperCase().startsWith("INSERT INTO " + expected.getKey() + " ")))
					.hasSize(expected.getValue().intValue());
		}
		assertThat(schema.columns()).hasSize(11);
		assertThat(schema.columns().values().stream().mapToInt(Map::size).sum()).isEqualTo(65);
		assertThat(schema.rules()).hasSize(61);
		assertThat(schema.rules().values().stream().filter(rule -> rule.type().equals("P"))).hasSize(11);
		assertThat(schema.rules().values().stream().filter(rule -> rule.type().equals("U"))).hasSize(8);
		assertThat(schema.rules().values().stream().filter(rule -> rule.type().equals("C"))).hasSize(25);
		assertThat(schema.rules().values().stream().filter(rule -> rule.type().equals("R"))).hasSize(17);
		assertThat(schema.rules().get("PK_EC_LINHA_ESTACAO").columns()).containsExactly("ID_LINHA", "ID_ESTACAO");
		assertThat(schema.rules().get("PK_EC_CONFIRMACAO").columns()).containsExactly("ID_OCORRENCIA", "ID_USUARIO");
		assertThat(schema.rules().get("PK_EC_ALERTA").columns()).containsExactly("ID_TRAJETO", "ID_OCORRENCIA");
	}

	@ParameterizedTest
	@ValueSource(strings = {"DROP TABLE T_EC_OPERADORA;", "DELETE FROM T_EC_OPERADORA;", "TRUNCATE TABLE T_EC_OPERADORA;"})
	void rejectsModifiedScriptsBeforeAnyConnection(String forbidden, @TempDir Path directory) throws Exception {
		Path script = directory.resolve(Bundle.DDL_FILE);
		Files.createDirectories(script.getParent());
		Files.writeString(script, bundle.ddl() + "\n" + forbidden, StandardCharsets.UTF_8);
		assertThatThrownBy(() -> Bundle.load(directory)).hasMessageContaining("Script alterado ou nao aprovado");
		verifyNoInteractions(oracle.dataSource);
	}

	@Test
	void inspectionUsesOnlySelectAndReportsTargetAndEmptyState() throws SQLException {
		tool.execute(request(Action.INSPECIONAR));
		assertThat(output()).contains("USER=RM562270; SCHEMA=RM562270; INSTANCIA=INSTANCE_TEST; SERVICO=SERVICE_TEST",
				"Estrutura: VAZIO", "Inspecao concluida; somente SELECT.");
		assertThat(oracle.reads).allMatch(sql -> sql.startsWith("SELECT "));
		assertNoWrites();
	}

	@ParameterizedTest
	@ValueSource(strings = {"user", "schema", "instance", "service", "empty-instance", "empty-service"})
	void blocksBothWritingActionsWhenTargetIsNotConfirmed(String mismatch) throws SQLException {
		oracle.completeStructure();
		Session identity = oracle.session;
		oracle.session = new Session(mismatch.equals("user") ? "OUTRO" : identity.user(),
				mismatch.equals("schema") ? "OUTRO" : identity.schema(),
				mismatch.equals("instance") ? "OUTRA" : mismatch.equals("empty-instance") ? "" : identity.instance(),
				mismatch.equals("service") ? "OUTRO" : mismatch.equals("empty-service") ? "" : identity.service(),
				identity.database(), identity.pdb());
		for (Action action : List.of(Action.DDL, Action.CARGA)) {
			assertThatThrownBy(() -> tool.execute(request(action))).hasMessageContaining("Destino nao confirmado");
		}
		assertNoWrites();
	}

	@Test
	void blocksDdlForPartialStructureAndForConflictingObject() throws SQLException {
		oracle.addTable("T_EC_OPERADORA");
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("DDL bloqueado");
		oracle.tables.clear();
		oracle.rules.clear();
		oracle.conflictingView = true;
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("DDL bloqueado");
		assertThat(output()).contains("Objeto conflitante/invalido: T_EC_OPERADORA");
		assertNoWrites();
	}

	@ParameterizedTest
	@ValueSource(strings = {"column-type", "column-default", "column-nullable", "check", "foreign-key",
			"foreign-key-target", "foreign-key-missing", "disabled", "not-validated", "trigger"})
	void blocksWritesForIncompatibleStructure(String mismatch) throws SQLException {
		oracle.completeStructure();
		oracle.columnChange = row -> {
			if ("ID_OPERADORA".equals(row.get("COLUMN_NAME"))) {
				if (mismatch.equals("column-type")) row.put("DATA_PRECISION", 4);
				if (mismatch.equals("column-default")) row.put("DATA_DEFAULT", "99");
				if (mismatch.equals("column-nullable")) row.put("NULLABLE", "Y");
			}
		};
		oracle.ruleChange = row -> {
			if (mismatch.equals("check") && "CK_EC_LOTACAO_NIVEL".equals(row.get("CONSTRAINT_NAME"))) row.put("SEARCH_CONDITION_VC", "NR_NIVEL > 99");
			if (mismatch.equals("foreign-key") && "FK_EC_LINHA_OPERADORA".equals(row.get("CONSTRAINT_NAME"))) row.put("R_OWNER", "OUTRO");
			if (mismatch.equals("foreign-key-target") && "FK_EC_LINHA_OPERADORA".equals(row.get("CONSTRAINT_NAME"))) row.put("R_CONSTRAINT_NAME", "PK_EC_USUARIO");
			if (mismatch.equals("foreign-key-missing") && "FK_EC_LINHA_OPERADORA".equals(row.get("CONSTRAINT_NAME"))) row.put("R_CONSTRAINT_NAME", "PK_DESCONHECIDA");
			if (mismatch.equals("disabled")) row.put("STATUS", "DISABLED");
			if (mismatch.equals("not-validated")) row.put("VALIDATED", "NOT VALIDATED");
		};
		oracle.enabledTrigger = mismatch.equals("trigger");
		for (Action action : List.of(Action.DDL, Action.CARGA)) {
			assertThatThrownBy(() -> tool.execute(request(action))).isInstanceOf(SetupFailure.class);
		}
		assertNoWrites();
	}

	@Test
	void ddlBlocksEvenCompatibleObjectsWithoutReadingDetailedMetadata() throws SQLException {
		oracle.completeStructure();
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("DDL bloqueado");
		assertThat(oracle.reads).hasSize(3).doesNotContain(COLUMNS_SQL, CONSTRAINTS_SQL, KEY_COLUMNS_SQL, TRIGGERS_SQL);
		assertNoWrites();
	}

	@Test
	void ddlCreatesOnlyTheApproved39StatementsAndNeverLoadsData() throws SQLException {
		tool.execute(request(Action.DDL));
		assertThat(oracle.readsBeforeFirstWrite).hasSize(3).allMatch(sql -> !sql.contains("ALL_") && !sql.contains(" JOIN "));
		assertThat(oracle.readsBeforeFirstWrite).doesNotContain(COLUMNS_SQL, CONSTRAINTS_SQL, KEY_COLUMNS_SQL, TRIGGERS_SQL);
		assertThat(oracle.writes).hasSize(39).allMatch(sql -> sql.startsWith("CREATE TABLE ")
				|| sql.startsWith("ALTER TABLE ") || sql.startsWith("COMMENT ON TABLE "));
		assertThat(oracle.writes).noneMatch(sql -> sql.startsWith("INSERT"));
		assertThat(tool.assess(tool.inspect(oracle.connection)).state()).isEqualTo(State.COMPATIVEL);
		assertThat(output()).contains("Carga nao executada.");
		verify(oracle.connection, never()).commit();
		verify(oracle.connection, never()).rollback();
	}

	@Test
	void ddlFailureReportsPartialStateAndBlocksAnAutomaticRetry() throws SQLException {
		oracle.failWriteAt = 4;
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("instrucao 4")
				.hasMessageContaining("ORA-01031").hasMessageContaining("SQLState=42000");
		assertThat(oracle.tables).hasSize(3);
		assertThat(output()).contains("nao houve rollback da estrutura", "Estado apos falha: REVISAO");
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("DDL bloqueado");
		assertThat(oracle.writes).hasSize(4);
		verify(oracle.connection, never()).rollback();
	}

	@Test
	void seedRefusesAnyExistingRowsWithoutChangingThem() throws SQLException {
		oracle.completeStructure();
		oracle.counts.put("T_EC_OPERADORA", 1L);
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).hasMessageContaining("todas as 11 tabelas devem estar vazias");
		assertThat(oracle.counts.get("T_EC_OPERADORA")).isEqualTo(1);
		assertNoWrites();
		verify(oracle.connection, never()).setAutoCommit(false);
	}

	@Test
	void seedCommitsAll132InsertsAndChecksAnotherConnection() throws SQLException {
		oracle.completeStructure();
		tool.execute(request(Action.CARGA));
		assertThat(oracle.writes).hasSize(132).allMatch(sql -> sql.startsWith("INSERT INTO "));
		assertThat(oracle.counts).containsExactlyEntriesOf(EXPECTED_ROWS);
		verify(oracle.connection).setAutoCommit(false);
		verify(oracle.connection).commit();
		verify(oracle.connection, never()).rollback();
		verify(oracle.dataSource, times(2)).getConnection();
		assertThat(output()).contains("COMMIT confirmado e 132 linhas verificadas por nova conexao.");
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).hasMessageContaining("Carga bloqueada");
		assertThat(oracle.writes).hasSize(132);
	}

	@Test
	void seedErrorRollsBackAllNewRowsAndNeverCommits() throws SQLException {
		oracle.completeStructure();
		oracle.failWriteAt = 8;
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).hasMessageContaining("Carga interrompida")
				.hasMessageContaining("instrucao 8");
		assertThat(oracle.counts.values()).containsOnly(0L);
		verify(oracle.connection).rollback();
		verify(oracle.connection, never()).commit();
	}

	@Test
	void anyCountMismatchRollsBackBeforeCommit() throws SQLException {
		oracle.completeStructure();
		oracle.skewCountAfterInserts = true;
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).hasMessageContaining("Contagens divergentes");
		verify(oracle.connection).rollback();
		verify(oracle.connection, never()).commit();
		assertThat(oracle.counts.values()).containsOnly(0L);
	}

	@Test
	void commitFailureIsIndeterminateAndDoesNotRevealDriverMessage() throws SQLException {
		oracle.completeStructure();
		doThrow(new SQLException("DB_PASS=SEGREDO_NAO_EXIBIR", "08006", 17002)).when(oracle.connection).commit();
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).hasMessageContaining("COMMIT INDETERMINADO")
				.hasMessageContaining("SQLState=08006").hasMessageNotContaining("SEGREDO_NAO_EXIBIR");
		assertThat(output()).doesNotContain("SEGREDO_NAO_EXIBIR", "COMMIT confirmado e 132");
		verify(oracle.connection).rollback();
	}

	@Test
	void rollbackFailureNeverEnablesAutoCommitToEndPendingWork() throws SQLException {
		oracle.completeStructure();
		oracle.failWriteAt = 1;
		doThrow(new SQLException("falha de rede", "08006", 17002)).when(oracle.connection).rollback();
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).isInstanceOf(SetupFailure.class);
		verify(oracle.connection, never()).setAutoCommit(true);
		verify(oracle.connection, never()).commit();
		assertThat(output()).contains("Rollback NAO CONFIRMADO");
	}

	@Test
	void failureOfPostCommitConnectionDoesNotClaimRollback() throws SQLException {
		oracle.completeStructure();
		when(oracle.dataSource.getConnection()).thenReturn(oracle.connection)
				.thenThrow(new SQLException("falha de rede", "08006", 17002));
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).hasMessageContaining("Carga ja recebeu COMMIT");
		verify(oracle.connection).commit();
		verify(oracle.connection, never()).rollback();
		assertThat(oracle.counts).containsExactlyEntriesOf(EXPECTED_ROWS);
	}

	@Test
	void closeFailureAfterCommitPreservesTheCommitStatus() throws SQLException {
		oracle.completeStructure();
		doThrow(new SQLException("falha de rede", "08006", 17002)).when(oracle.connection).close();
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA)))
				.hasMessageContaining("Carga ja recebeu COMMIT; falha ao encerrar a conexao");
		verify(oracle.connection).commit();
		verify(oracle.connection, never()).rollback();
		assertThat(oracle.counts).containsExactlyEntriesOf(EXPECTED_ROWS);
	}

	@Test
	void metadataAccessFailureStopsBeforeAnyWrite() throws SQLException {
		oracle.completeStructure();
		oracle.failedRead = COLUMNS_SQL;
		assertThatThrownBy(() -> tool.execute(request(Action.CARGA))).isInstanceOf(SetupFailure.class)
				.hasMessageContaining("USER_TAB_COLUMNS INCONCLUSIVA");
		assertNoWrites();
	}

	@Test
	void emptyInspectionSkipsUnnecessaryMetadataQueriesAndCounts() throws SQLException {
		oracle.failedRead = COLUMNS_SQL;
		tool.execute(request(Action.INSPECIONAR));
		assertThat(oracle.reads).hasSize(3).doesNotContain(COLUMNS_SQL, CONSTRAINTS_SQL, KEY_COLUMNS_SQL, TRIGGERS_SQL);
		assertThat(oracle.reads).allMatch(sql -> !sql.contains("ALL_") && !sql.contains(" JOIN ") && !sql.contains("COUNT(*)"));
		assertThat(output()).contains("Estrutura: VAZIO", "Metadados detalhados e contagens dispensados");
		assertNoWrites();
	}

	@ParameterizedTest
	@ValueSource(strings = {"session", "objects", "reserved"})
	void ora1013AtAnyPreflightStepBlocksDdlAndIdentifiesTheQuery(String stage) throws SQLException {
		oracle.failureStage = stage;
		oracle.readFailure = new SQLException("cancelamento com dado sensivel", "72000", 1013);
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("INCONCLUSIVA")
				.hasMessageContaining("ORA-01013").hasMessageContaining("SQLState=72000").hasMessageNotContaining("dado sensivel");
		assertThat(output()).contains("timeout=30s", "INCONCLUSIVA").doesNotContain("Pre-verificacao DDL concluida", "Estrutura: VAZIO");
		assertNoWrites();
	}

	@Test
	void jdbcTimeoutWithoutOracleCodeAlsoBlocksDdl() throws SQLException {
		oracle.failureStage = "objects";
		oracle.readFailure = new SQLTimeoutException("driver timeout", "HYT00");
		assertThatThrownBy(() -> tool.execute(request(Action.DDL)))
				.hasMessageContaining("USER_OBJECTS").hasMessageContaining("timeout JDBC");
		assertNoWrites();
	}

	@Test
	void cancellationDuringResultFetchingNeverUsesPartialObjectsAsACompletedPreflight() throws SQLException {
		oracle.failDuringObjectsFetch = true;
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("USER_OBJECTS")
				.hasMessageContaining("ORA-01013");
		assertThat(output()).doesNotContain("Pre-verificacao DDL concluida");
		assertNoWrites();
	}

	@ParameterizedTest
	@ValueSource(strings = {"no-session", "ambiguous-session", "unknown-object", "unknown-constraint"})
	void inconclusiveCatalogResultsAlwaysBlockDdl(String incomplete) throws SQLException {
		oracle.incomplete = incomplete;
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).isInstanceOf(SetupFailure.class);
		assertNoWrites();
	}

	@ParameterizedTest
	@ValueSource(strings = {"index", "constraint"})
	void reservedNamesOutsideTheElevenTablesAlsoBlockDdl(String conflict) throws SQLException {
		oracle.orphanIndex = conflict.equals("index");
		oracle.orphanConstraint = conflict.equals("constraint");
		assertThatThrownBy(() -> tool.execute(request(Action.DDL))).hasMessageContaining("DDL bloqueado");
		assertNoWrites();
	}

	@Test
	void exactDocumentedDdlArgumentsAreAcceptedAndConfirmTheReportedTarget() throws SQLException {
		oracle.session = new Session(OWNER, OWNER, "cdb1", "orcl", "DATABASE_TEST", "ORCL");
		tool.execute(Request.parse("--acao=ddl", "--instancia-esperada=cdb1", "--servico-esperado=orcl"));
		assertThat(oracle.writes).hasSize(39);
		assertThat(output()).contains("INSTANCIA=cdb1; SERVICO=orcl", "Carga nao executada.");
	}

	@Test
	void validationUsesOnlySelectAndRunsTheThreeDqlQueries() throws SQLException {
		oracle.completeStructure();
		oracle.counts.putAll(EXPECTED_ROWS);
		tool.execute(request(Action.VALIDAR));
		assertThat(output()).contains("Total encontrado=132; esperado=132.", "DQL consulta 3: SELECT concluido");
		assertNoWrites();
	}

	@Test
	void reportsSqlAccessFailureWithoutRawCredentials() throws SQLException {
		when(oracle.dataSource.getConnection()).thenThrow(new SQLException("senha=SEGREDO", "42000", 1031));
		assertThatThrownBy(() -> tool.execute(request(Action.INSPECIONAR))).isInstanceOf(SQLException.class);
		assertThat(safeFailure(new SQLException("senha=SEGREDO", "42000", 1031)))
				.contains("ORA-01031", "permissao insuficiente", "SQLState=42000").doesNotContain("SEGREDO");
		assertThat(safeFailure(new IllegalArgumentException("senha=SEGREDO"))).doesNotContain("SEGREDO");
		assertNoWrites();
	}

	private Request request(Action action) { return new Request(action, "INSTANCE_TEST", "SERVICE_TEST"); }
	private String output() { return console.toString(StandardCharsets.UTF_8); }
	private void assertNoWrites() throws SQLException {
		assertThat(oracle.writes).isEmpty();
		verify(oracle.connection, never()).commit();
		verify(oracle.connection, never()).rollback();
	}

	/** Simula catalogos e transacao em memoria; nao possui driver nem conexao Oracle real. */
	private static final class FakeOracle {
		final DataSource dataSource = mock(DataSource.class);
		final Connection connection = mock(Connection.class);
		final Set<String> tables = new LinkedHashSet<>();
		final Set<String> rules = new LinkedHashSet<>();
		final Map<String, Long> counts = new LinkedHashMap<>();
		final List<String> reads = new ArrayList<>();
		final List<String> writes = new ArrayList<>();
		final List<String> readsBeforeFirstWrite = new ArrayList<>();
		final Set<String> dqlQueries;
		final Schema schema;
		Session session = new Session(OWNER, OWNER, "INSTANCE_TEST", "SERVICE_TEST", "DATABASE_TEST", "PDB_TEST");
		Consumer<Map<String, Object>> columnChange = row -> { };
		Consumer<Map<String, Object>> ruleChange = row -> { };
		boolean conflictingView;
		boolean enabledTrigger;
		boolean skewCountAfterInserts;
		String failedRead;
		String failureStage;
		String incomplete;
		SQLException readFailure = new SQLException("permissao insuficiente", "42000", 1031);
		boolean failDuringObjectsFetch;
		boolean orphanIndex;
		boolean orphanConstraint;
		int failWriteAt;

		FakeOracle(Schema schema, String dql) throws SQLException {
			this.schema = schema;
			this.dqlQueries = Set.copyOf(splitTopLevel(withoutComments(dql), ';'));
			EXPECTED_ROWS.keySet().forEach(table -> counts.put(table, 0L));
			when(dataSource.getConnection()).thenReturn(connection);
			when(connection.createStatement()).thenAnswer(invocation -> {
				Statement statement = mock(Statement.class);
				when(statement.executeQuery(anyString())).thenAnswer(query -> read(query.getArgument(0)));
				when(statement.execute(anyString())).thenAnswer(query -> write(query.getArgument(0)));
				when(statement.getUpdateCount()).thenReturn(-1);
				return statement;
			});
			doAnswer(invocation -> { counts.replaceAll((table, count) -> 0L); return null; }).when(connection).rollback();
		}

		void addTable(String table) {
			tables.add(table);
			schema.rules().forEach((name, rule) -> { if (rule.table().equals(table) && !rule.type().equals("R")) rules.add(name); });
		}

		void completeStructure() { EXPECTED_ROWS.keySet().forEach(this::addTable); rules.addAll(schema.rules().keySet()); }

		boolean write(String sql) throws SQLException {
			if (writes.isEmpty()) readsBeforeFirstWrite.addAll(reads);
			writes.add(sql);
			if (failWriteAt == writes.size()) throw new SQLException("credencial=SEGREDO", "42000", 1031);
			var create = Pattern.compile("(?i)^CREATE TABLE (\\w+)").matcher(sql);
			var foreignKey = Pattern.compile("(?i)^ALTER TABLE \\w+ ADD CONSTRAINT (\\w+)").matcher(sql);
			var insert = Pattern.compile("(?i)^INSERT INTO (\\w+)").matcher(sql);
			if (create.find()) addTable(create.group(1).toUpperCase());
			else if (foreignKey.find()) rules.add(foreignKey.group(1).toUpperCase());
			else if (insert.find()) counts.merge(insert.group(1).toUpperCase(), 1L, Long::sum);
			else if (!sql.startsWith("COMMENT ON TABLE ")) throw new AssertionError("SQL de escrita inesperado no mock: " + sql);
			return false;
		}

		ResultSet read(String sql) throws SQLException {
			reads.add(sql);
			boolean objectsQuery = sql.contains("FROM user_objects");
			boolean reservedQuery = sql.contains("FROM user_constraints WHERE constraint_name IN");
			if (sql.equals(failedRead) || "session".equals(failureStage) && sql.equals(SESSION_SQL)
					|| "objects".equals(failureStage) && objectsQuery || "reserved".equals(failureStage) && reservedQuery) throw readFailure;
			var rows = new ArrayList<Map<String, Object>>();
			if (sql.equals(SESSION_SQL)) {
				if (!"no-session".equals(incomplete)) rows.add(row("USUARIO", session.user(), "SCHEMA_ATUAL", session.schema(),
						"INSTANCIA", session.instance(), "SERVICO", session.service(), "BANCO", session.database(), "PDB", session.pdb()));
				if ("ambiguous-session".equals(incomplete)) rows.add(rows.getFirst());
			} else if (objectsQuery) {
				tables.forEach(table -> rows.add(row("OBJECT_NAME", table, "OBJECT_TYPE", "TABLE", "STATUS", "VALID")));
				rules.forEach(name -> { if (Set.of("P", "U").contains(schema.rules().get(name).type()))
					rows.add(row("OBJECT_NAME", name, "OBJECT_TYPE", "INDEX", "STATUS", "VALID")); });
				if (conflictingView) rows.add(row("OBJECT_NAME", "T_EC_OPERADORA", "OBJECT_TYPE", "VIEW", "STATUS", "VALID"));
				if (orphanIndex) rows.add(row("OBJECT_NAME", "PK_EC_OPERADORA", "OBJECT_TYPE", "INDEX", "STATUS", "VALID"));
				if ("unknown-object".equals(incomplete)) rows.add(row("OBJECT_NAME", "T_EC_OPERADORA", "OBJECT_TYPE", null, "STATUS", "VALID"));
				if (failDuringObjectsFetch) rows.add(row("OBJECT_NAME", "T_EC_OPERADORA", "OBJECT_TYPE", "TABLE", "STATUS", "VALID"));
			} else if (reservedQuery) {
				rules.forEach(name -> rows.add(row("CONSTRAINT_NAME", name, "TABLE_NAME", schema.rules().get(name).table())));
				if (orphanConstraint) rows.add(row("CONSTRAINT_NAME", "FK_EC_LINHA_OPERADORA", "TABLE_NAME", "OUTRA_TABELA"));
				if ("unknown-constraint".equals(incomplete)) rows.add(row("CONSTRAINT_NAME", "PK_EC_OPERADORA", "TABLE_NAME", null));
			} else if (sql.equals(COLUMNS_SQL)) {
				for (String table : tables) schema.columns().get(table).forEach((name, column) -> {
					var metadata = row("TABLE_NAME", table, "COLUMN_NAME", name, "DATA_TYPE", column.type(),
							"DATA_PRECISION", column.precision(), "DATA_SCALE", column.scale(), "DATA_LENGTH", column.length(),
							"CHAR_LENGTH", column.length(), "CHAR_USED", "B", "NULLABLE", column.nullable() ? "Y" : "N", "DATA_DEFAULT", column.defaultValue());
					columnChange.accept(metadata);
					rows.add(metadata);
				});
			} else if (sql.equals(KEY_COLUMNS_SQL)) {
				for (String name : rules) {
					Rule rule = schema.rules().get(name);
					for (int i = 0; i < rule.columns().size(); i++) rows.add(row("CONSTRAINT_NAME", name,
							"COLUMN_NAME", rule.columns().get(i), "REFERENCED_COLUMN", rule.type().equals("R") ? rule.referenceColumns().get(i) : null));
				}
			} else if (sql.equals(CONSTRAINTS_SQL)) {
				for (String name : rules) {
					Rule rule = schema.rules().get(name);
					String parent = schema.rules().entrySet().stream().filter(entry ->
							Set.of("P", "U").contains(entry.getValue().type())
							&& entry.getValue().table().equals(rule.referenceTable())
							&& entry.getValue().columns().equals(rule.referenceColumns()))
							.map(Map.Entry::getKey).findFirst().orElse(null);
					var metadata = row("CONSTRAINT_NAME", name, "TABLE_NAME", rule.table(), "CONSTRAINT_TYPE", rule.type(),
							"STATUS", "ENABLED", "VALIDATED", "VALIDATED", "DEFERRABLE", "NOT DEFERRABLE", "DEFERRED", "IMMEDIATE",
							"DELETE_RULE", "NO ACTION", "R_OWNER", rule.referenceOwner(), "REFERENCED_TABLE", rule.referenceTable(),
							"R_CONSTRAINT_NAME", parent, "GENERATED", "USER NAME", "SEARCH_CONDITION_VC", rule.condition());
					ruleChange.accept(metadata);
					rows.add(metadata);
				}
			} else if (sql.equals(TRIGGERS_SQL)) {
				if (enabledTrigger) rows.add(row("TRIGGER_NAME", "TRIGGER_NAO_PREVISTA"));
			} else if (sql.startsWith("SELECT COUNT(*) FROM ")) {
				var table = Pattern.compile("\\.\"(T_EC_\\w+)\"$").matcher(sql);
				if (!table.find()) throw new AssertionError("Contagem sem OWNER explicito.");
				long count = counts.get(table.group(1));
				if (skewCountAfterInserts && writes.size() == 132 && table.group(1).equals("T_EC_OPERADORA")) count++;
				rows.add(row("1", count));
			} else if (!dqlQueries.contains(sql)) throw new AssertionError("Leitura nao prevista no mock: " + sql);
			return result(rows, objectsQuery && failDuringObjectsFetch);
		}
	}

	private static Map<String, Object> row(Object... entries) {
		var row = new LinkedHashMap<String, Object>();
		for (int i = 0; i < entries.length; i += 2) row.put((String) entries[i], entries[i + 1]);
		return row;
	}

	private static ResultSet result(List<Map<String, Object>> rows, boolean failAfterFirstRow) throws SQLException {
		ResultSet result = mock(ResultSet.class);
		var index = new AtomicInteger(-1);
		var lastValue = new AtomicReference<Object>();
		when(result.next()).thenAnswer(invocation -> {
			int current = index.incrementAndGet();
			if (failAfterFirstRow && current == 1) throw new SQLException("consulta cancelada", "72000", 1013);
			return current < rows.size();
		});
		when(result.getString(anyString())).thenAnswer(invocation -> {
			Object value = rows.get(index.get()).get(invocation.getArgument(0));
			return value == null ? null : value.toString();
		});
		when(result.getInt(anyString())).thenAnswer(invocation -> {
			Object value = rows.get(index.get()).get(invocation.getArgument(0));
			lastValue.set(value);
			return value == null ? 0 : ((Number) value).intValue();
		});
		when(result.wasNull()).thenAnswer(invocation -> lastValue.get() == null);
		when(result.getLong(anyInt())).thenAnswer(invocation -> {
			Object value = rows.get(index.get()).get("1");
			lastValue.set(value);
			return value == null ? 0L : ((Number) value).longValue();
		});
		return result;
	}
}
