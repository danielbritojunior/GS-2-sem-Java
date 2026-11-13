package br.com.fiap.embarqueCertoAPI.maintenance;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Ferramenta local manual. Nao e componente da API nem teste de integracao automatico. */
public final class OracleDatabaseSetupManual {

	static final String OWNER = "RM562270";
	static final int QUERY_TIMEOUT_SECONDS = 30;
	static final Map<String, Long> EXPECTED_ROWS = expectedRows();
	static final String TABLE_FILTER = "(" + names(EXPECTED_ROWS.keySet()) + ")";
	static final String SESSION_SQL = "SELECT USER AS usuario, "
			+ "SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS schema_atual, "
			+ "SYS_CONTEXT('USERENV', 'INSTANCE_NAME') AS instancia, "
			+ "SYS_CONTEXT('USERENV', 'SERVICE_NAME') AS servico, "
			+ "SYS_CONTEXT('USERENV', 'DB_NAME') AS banco, "
			+ "SYS_CONTEXT('USERENV', 'CON_NAME') AS pdb FROM dual";
	static final String COLUMNS_SQL = "SELECT table_name, column_name, data_type, data_precision, data_scale, "
			+ "data_length, char_length, char_used, nullable, data_default FROM user_tab_columns "
			+ "WHERE table_name IN " + TABLE_FILTER + " ORDER BY table_name, column_id";
	static final String CONSTRAINTS_SQL = "SELECT table_name, constraint_name, constraint_type, status, "
			+ "validated, deferrable, deferred, delete_rule, r_owner, r_constraint_name, generated, "
			+ "search_condition_vc FROM user_constraints WHERE table_name IN " + TABLE_FILTER
			+ " ORDER BY constraint_name";
	static final String KEY_COLUMNS_SQL = "SELECT constraint_name, column_name, position FROM user_cons_columns "
			+ "WHERE table_name IN " + TABLE_FILTER + " ORDER BY constraint_name, position";
	static final String TRIGGERS_SQL = "SELECT trigger_name FROM user_triggers WHERE table_name IN "
			+ TABLE_FILTER + " AND status = 'ENABLED' ORDER BY trigger_name";

	enum Action { INSPECIONAR, DDL, CARGA, VALIDAR }
	enum State { VAZIO, COMPATIVEL, REVISAO }

	static final class SetupFailure extends IllegalStateException {
		SetupFailure(String message) { super(message); }
		SetupFailure(String message, Throwable cause) { super(message, cause); }
	}

	record Request(Action action, String instance, String service) {
		static Request parse(String... args) {
			var options = new DefaultApplicationArguments(args);
			var actions = options.getOptionValues("acao");
			if (actions == null) return null;
			if (actions.size() != 1) throw new SetupFailure("Informe exatamente uma --acao.");
			Action action;
			try {
				action = Action.valueOf(actions.getFirst().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException exception) {
				throw new SetupFailure("Acao invalida: use inspecionar, ddl, carga ou validar.");
			}
			String instance = singleOption(options, "instancia-esperada");
			String service = singleOption(options, "servico-esperado");
			if ((action == Action.DDL || action == Action.CARGA) && (blank(instance) || blank(service))) {
				throw new SetupFailure("Escritas exigem --instancia-esperada e --servico-esperado, "
						+ "obtidos na inspecao real. Nenhuma conexao foi aberta.");
			}
			return new Request(action, instance, service);
		}

		private static String singleOption(DefaultApplicationArguments args, String name) {
			var values = args.getOptionValues(name);
			if (values == null) return null;
			if (values.size() != 1 || blank(values.getFirst())) {
				throw new SetupFailure("Informe um valor para --" + name + ".");
			}
			return values.getFirst();
		}
	}

	record Session(String user, String schema, String instance, String service, String database, String pdb) {
		void confirm(Request request) {
			if (!OWNER.equals(user) || !OWNER.equals(schema) || blank(instance) || blank(service)
					|| !instance.equals(request.instance()) || !service.equals(request.service())) {
				throw new SetupFailure("Destino nao confirmado: USER/SCHEMA devem ser " + OWNER
						+ "; instancia e servico devem coincidir exatamente com os valores da inspecao.");
			}
		}
	}

	record Column(String type, Integer precision, Integer scale, Integer length, boolean nullable, String defaultValue) { }
	record Rule(String table, String type, List<String> columns, String condition,
			String referenceOwner, String referenceTable, List<String> referenceColumns) { }
	record Assessment(State state, List<String> differences) { }
	record Snapshot(Session session, Set<String> tables, Map<String, Map<String, Column>> columns,
			Map<String, Rule> rules, List<String> problems, Map<String, Long> counts, boolean detailsChecked) { }
	record FoundObject(String name, String type, String status) { }
	record Namespace(Session session, Set<String> tables, List<FoundObject> objects,
			Set<String> reservedConstraints, List<String> problems) {
		boolean empty() { return objects.isEmpty() && reservedConstraints.isEmpty() && problems.isEmpty(); }
	}
	record ForeignReference(String owner, String constraint) { }

	/** Contexto somente de DataSource, explicitamente fora do component scan da API. */
	@TestConfiguration(proxyBeanMethods = false)
	@ImportAutoConfiguration(DataSourceAutoConfiguration.class)
	static class DataSourceOnlyConfiguration { }

	public static void main(String[] args) {
		int status = 0;
		try {
			Request request = Request.parse(args);
			if (request == null) {
				System.out.println("Ferramenta manual: --acao=inspecionar|ddl|carga|validar. "
						+ "Sem acao, nenhuma conexao e aberta.");
				return;
			}
			Bundle bundle = Bundle.load(Path.of(""));
			try (var context = new SpringApplicationBuilder(DataSourceOnlyConfiguration.class)
					.web(WebApplicationType.NONE).logStartupInfo(false).run(args)) {
				new OracleDatabaseSetupManual(context.getBean(DataSource.class), bundle, System.out).execute(request);
			}
		} catch (Exception exception) {
			status = 1;
			System.err.println("[Preparacao Oracle] " + safeFailure(exception));
		}
		if (status != 0) System.exit(status);
	}

	private final DataSource dataSource;
	private final Bundle bundle;
	private final Schema schema;
	private final PrintStream out;

	OracleDatabaseSetupManual(DataSource dataSource, Bundle bundle, PrintStream out) {
		this.dataSource = dataSource;
		this.bundle = bundle;
		this.schema = Schema.parse(bundle.ddl());
		this.out = out;
	}

	void execute(Request request) throws SQLException {
		boolean committed = false;
		try (var connection = dataSource.getConnection()) {
			Session session = readSession(connection);
			if (request.action() == Action.DDL || request.action() == Action.CARGA) session.confirm(request);
			Namespace namespace = readNamespace(connection, session);
			if (request.action() == Action.DDL) {
				require(namespace.empty(), "DDL bloqueado: ha objetos ou nomes de constraints previstos ja existentes. "
						+ "Nenhum DDL foi executado; revisar manualmente.");
				out.println("[Preparacao Oracle] Pre-verificacao DDL concluida: identidade confirmada e todos os nomes previstos livres.");
				createStructure(connection);
			} else {
				Snapshot snapshot = inspectDetails(connection, namespace);
				Assessment assessment = assess(snapshot);
				out.println("[Preparacao Oracle] Estrutura: " + assessment.state());
				assessment.differences().forEach(difference -> out.println("  REVISAR: " + difference));
				switch (request.action()) {
					case INSPECIONAR -> out.println("[Preparacao Oracle] Inspecao concluida; somente SELECT.");
					case CARGA -> {
						require(assessment.state() == State.COMPATIVEL, "Carga bloqueada: estrutura nao compativel.");
						require(snapshot.counts().size() == EXPECTED_ROWS.size()
								&& snapshot.counts().values().stream().allMatch(count -> count == 0),
								"Carga bloqueada: todas as 11 tabelas devem estar vazias; dados existentes serao preservados.");
						loadData(connection);
						committed = true;
					}
					case VALIDAR -> {
						require(assessment.state() == State.COMPATIVEL, "Validacao bloqueada: estrutura nao compativel.");
						verifyCounts(connection);
						runQueries(connection);
					}
				}
			}
		} catch (SQLException | RuntimeException exception) {
			if (committed) {
				throw new SetupFailure("Carga ja recebeu COMMIT; falha ao encerrar a conexao. "
						+ "Verificacao posterior pendente; executar inspecionar antes de repetir. " + safeFailure(exception), exception);
			}
			throw exception;
		}
		if (committed) {
			try (var verification = dataSource.getConnection()) {
				verifyCounts(verification);
				out.println("[Preparacao Oracle] COMMIT confirmado e 132 linhas verificadas por nova conexao.");
			} catch (SQLException | RuntimeException exception) {
				throw new SetupFailure("Carga ja recebeu COMMIT; verificacao posterior falhou. "
						+ "Nao repetir a carga: executar inspecionar. " + safeFailure(exception), exception);
			}
		}
	}

	Snapshot inspect(Connection connection) throws SQLException {
		return inspectDetails(connection, readNamespace(connection, readSession(connection)));
	}

	private Session readSession(Connection connection) throws SQLException {
		var sessions = new ArrayList<Session>();
		query(connection, "IDENTIDADE DA SESSAO", SESSION_SQL, result -> sessions.add(new Session(
				result.getString("USUARIO"), result.getString("SCHEMA_ATUAL"), result.getString("INSTANCIA"),
				result.getString("SERVICO"), result.getString("BANCO"), result.getString("PDB"))));
		require(sessions.size() == 1, "Destino nao confirmado: identidade da sessao ausente ou ambigua.");
		Session session = sessions.getFirst();
		require(!blank(session.user()) && !blank(session.schema()) && !blank(session.instance()) && !blank(session.service()),
				"Destino nao confirmado: identidade da sessao incompleta.");
		out.printf("[Preparacao Oracle] USER=%s; SCHEMA=%s; INSTANCIA=%s; SERVICO=%s; BANCO=%s; PDB=%s%n",
				session.user(), session.schema(), session.instance(), session.service(), session.database(), session.pdb());
		return session;
	}

	private Namespace readNamespace(Connection connection, Session session) throws SQLException {
		var tables = new LinkedHashSet<String>();
		var problems = new ArrayList<String>();
		var namespaceNames = new LinkedHashSet<>(EXPECTED_ROWS.keySet());
		schema.rules().forEach((name, rule) -> { if (Set.of("P", "U").contains(rule.type())) namespaceNames.add(name); });
		var objects = new ArrayList<FoundObject>();
		String objectSql = "SELECT object_name, object_type, status FROM user_objects WHERE object_name IN ("
				+ names(namespaceNames) + ") ORDER BY object_name";
		query(connection, "USER_OBJECTS (11 tabelas e indices previstos)", objectSql, result -> {
			String name = result.getString("OBJECT_NAME");
			String type = result.getString("OBJECT_TYPE");
			String status = result.getString("STATUS");
			require(namespaceNames.contains(name) && !blank(type) && !blank(status), "Resultado de USER_OBJECTS inconclusivo.");
			objects.add(new FoundObject(name, type, status));
			out.printf("  OBJETO: %s; TIPO=%s; STATUS=%s%n", name, type, status);
			if (EXPECTED_ROWS.containsKey(name) && "TABLE".equals(type)) tables.add(name);
			Rule rule = schema.rules().get(name);
			boolean allowed = EXPECTED_ROWS.containsKey(name) ? "TABLE".equals(type)
					: rule != null && Set.of("P", "U").contains(rule.type()) && "INDEX".equals(type);
			if (!allowed || !"VALID".equals(status)) problems.add("Objeto conflitante/invalido: " + name);
		});
		if (tables.isEmpty() && !objects.isEmpty()) problems.add("Objetos previstos ja ocupam nomes do projeto.");
		var reserved = new LinkedHashSet<String>();
		String reservedSql = "SELECT constraint_name, table_name FROM user_constraints WHERE constraint_name IN ("
				+ names(schema.rules().keySet()) + ") ORDER BY constraint_name";
		query(connection, "USER_CONSTRAINTS (colisoes de nomes)", reservedSql, result -> {
			String name = result.getString("CONSTRAINT_NAME");
			String table = result.getString("TABLE_NAME");
			require(schema.rules().containsKey(name) && !blank(table) && reserved.add(name),
					"Resultado de nomes de constraints inconclusivo.");
			out.printf("  CONSTRAINT EXISTENTE: %s; TABELA=%s%n", name, table);
			if (!schema.rules().get(name).table().equals(table) || !tables.contains(table)) {
				problems.add("Nome de constraint ocupado ou inconsistente: " + name + " em " + table);
			}
		});
		out.println("[Preparacao Oracle] Tabelas autorizadas do usuario conectado: " + tables.size() + "/11.");
		problems.forEach(problem -> out.println("  REVISAR: " + problem));
		return new Namespace(session, tables, objects, reserved, problems);
	}

	private Snapshot inspectDetails(Connection connection, Namespace namespace) throws SQLException {
		var tables = namespace.tables();
		var problems = new ArrayList<>(namespace.problems());
		if (!tables.equals(EXPECTED_ROWS.keySet()) || !problems.isEmpty()) {
			out.println("[Preparacao Oracle] Metadados detalhados e contagens dispensados: conjunto vazio, parcial ou conflitante.");
			return new Snapshot(namespace.session(), tables, Map.of(), Map.of(), problems, Map.of(), false);
		}

		var columns = new LinkedHashMap<String, Map<String, Column>>();
		query(connection, "USER_TAB_COLUMNS", COLUMNS_SQL, result -> {
			String tableName = result.getString("TABLE_NAME");
			String columnName = result.getString("COLUMN_NAME");
			String type = result.getString("DATA_TYPE");
			Integer length = Set.of("CHAR", "VARCHAR2").contains(type)
					? result.getInt("C".equals(result.getString("CHAR_USED")) ? "CHAR_LENGTH" : "DATA_LENGTH") : null;
			var column = new Column(type, integer(result, "DATA_PRECISION"), integer(result, "DATA_SCALE"), length,
					"Y".equals(result.getString("NULLABLE")), normalize(result.getString("DATA_DEFAULT")));
			columns.computeIfAbsent(tableName, ignored -> new LinkedHashMap<>()).put(columnName, column);
		});

		var keyColumns = new LinkedHashMap<String, List<String>>();
		query(connection, "USER_CONS_COLUMNS", KEY_COLUMNS_SQL, result -> {
			String name = result.getString("CONSTRAINT_NAME");
			keyColumns.computeIfAbsent(name, ignored -> new ArrayList<>()).add(result.getString("COLUMN_NAME"));
		});
		var rules = new LinkedHashMap<String, Rule>();
		var foreignReferences = new LinkedHashMap<String, ForeignReference>();
		query(connection, "USER_CONSTRAINTS (estrutura)", CONSTRAINTS_SQL, result -> {
			String name = result.getString("CONSTRAINT_NAME");
			String table = result.getString("TABLE_NAME");
			String type = result.getString("CONSTRAINT_TYPE");
			String condition = normalize(result.getString("SEARCH_CONDITION_VC"));
			// NOT NULL sem nome e verificado por USER_TAB_COLUMNS.NULLABLE.
			if ("C".equals(type) && "GENERATED NAME".equals(result.getString("GENERATED"))
					&& columns.getOrDefault(table, Map.of()).entrySet().stream()
							.anyMatch(column -> !column.getValue().nullable()
									&& (column.getKey() + "ISNOTNULL").equals(condition))) return;
			if (!"ENABLED".equals(result.getString("STATUS")) || !"VALIDATED".equals(result.getString("VALIDATED"))
					|| !"NOT DEFERRABLE".equals(result.getString("DEFERRABLE"))
					|| !"IMMEDIATE".equals(result.getString("DEFERRED"))) problems.add("Constraint sem integridade ativa: " + name);
			if ("R".equals(type) && !"NO ACTION".equals(result.getString("DELETE_RULE"))) problems.add("Regra de exclusao divergente: " + name);
			rules.put(name, new Rule(table, type, "C".equals(type) ? List.of() : keyColumns.getOrDefault(name, List.of()),
					"C".equals(type) ? condition : null, null, null, List.of()));
			if ("R".equals(type)) foreignReferences.put(name,
					new ForeignReference(result.getString("R_OWNER"), result.getString("R_CONSTRAINT_NAME")));
		});
		var resolvedRules = new LinkedHashMap<>(rules);
		foreignReferences.forEach((name, reference) -> {
			Rule foreign = rules.get(name);
			Rule parent = rules.get(reference.constraint());
			boolean local = namespace.session().user().equals(reference.owner()) && parent != null
					&& Set.of("P", "U").contains(parent.type());
			if (!local) problems.add("Referencia de FK externa ou inconclusiva: " + name);
			resolvedRules.put(name, new Rule(foreign.table(), foreign.type(), foreign.columns(), null,
					reference.owner(), local ? parent.table() : null,
					local ? keyColumns.getOrDefault(reference.constraint(), List.of()) : List.of()));
		});
		query(connection, "USER_TRIGGERS", TRIGGERS_SQL,
				result -> problems.add("Trigger habilitada nao prevista no DDL: " + result.getString("TRIGGER_NAME")));
		var counts = new LinkedHashMap<String, Long>();
		for (String table : tables) {
			long count = count(connection, namespace.session().user(), table);
			counts.put(table, count);
			out.printf("  REGISTROS: %s=%d; esperado na carga=%d%n", table, count, EXPECTED_ROWS.get(table));
		}
		return new Snapshot(namespace.session(), tables, columns, resolvedRules, problems, counts, true);
	}

	Assessment assess(Snapshot snapshot) {
		var differences = new ArrayList<>(snapshot.problems());
		if (snapshot.tables().isEmpty() && snapshot.columns().isEmpty() && snapshot.rules().isEmpty() && differences.isEmpty()) {
			return new Assessment(State.VAZIO, List.of());
		}
		if (!snapshot.detailsChecked()) {
			EXPECTED_ROWS.keySet().stream().filter(table -> !snapshot.tables().contains(table))
					.forEach(table -> differences.add("Tabela ausente: " + table));
			differences.add("Estrutura parcial ou conflitante: revisao manual necessaria; detalhes nao consultados.");
			return new Assessment(State.REVISAO, List.copyOf(differences));
		}
		for (String table : EXPECTED_ROWS.keySet()) {
			if (!snapshot.tables().contains(table)) differences.add("Tabela ausente: " + table);
			if (!Objects.equals(schema.columns().get(table), snapshot.columns().get(table))) {
				differences.add("Colunas/tipos/tamanhos/defaults/nullabilidade divergentes: " + table);
			}
		}
		for (var rule : schema.rules().entrySet()) {
			if (!rule.getValue().equals(snapshot.rules().get(rule.getKey()))) differences.add("Constraint ausente/divergente: " + rule.getKey());
		}
		for (String name : snapshot.rules().keySet()) {
			if (!schema.rules().containsKey(name)) differences.add("Constraint adicional nao prevista: " + name);
		}
		return new Assessment(differences.isEmpty() ? State.COMPATIVEL : State.REVISAO, List.copyOf(differences));
	}

	private void createStructure(Connection connection) throws SQLException {
		try {
			executeScript(connection, bundle.ddl(), Bundle.DDL_FILE);
			require(assess(inspect(connection)).state() == State.COMPATIVEL, "DDL terminou, mas a estrutura exige revisao.");
			out.println("[Preparacao Oracle] DDL concluido e estrutura conferida. Carga nao executada.");
		} catch (SQLException | RuntimeException exception) {
			out.println("[Preparacao Oracle] DDL interrompido. DDL pode ter commits implicitos; nao houve rollback da estrutura.");
			try {
				Assessment after = assess(inspect(connection));
				out.println("[Preparacao Oracle] Estado apos falha: " + after.state());
				after.differences().forEach(out::println);
			} catch (SQLException | RuntimeException inspectionFailure) {
				out.println("[Preparacao Oracle] Estado apos falha INDETERMINADO: " + safeFailure(inspectionFailure));
			}
			throw new SetupFailure("DDL falhou; revisar inventario antes de retomar. " + safeFailure(exception), exception);
		}
	}

	private void loadData(Connection connection) throws SQLException {
		boolean commitAttempted = false;
		try {
			connection.setAutoCommit(false);
			executeScript(connection, bundle.dml(), Bundle.DML_FILE);
			verifyCounts(connection);
			commitAttempted = true;
			connection.commit();
		} catch (SQLException | RuntimeException exception) {
			try {
				connection.rollback();
				out.println("[Preparacao Oracle] Rollback da transacao solicitado com sucesso.");
			} catch (SQLException rollbackFailure) {
				out.println("[Preparacao Oracle] Rollback NAO CONFIRMADO: " + safeFailure(rollbackFailure));
			}
			String detail = commitAttempted ? "Resultado do COMMIT INDETERMINADO; inspecionar antes de repetir. "
					: "Carga interrompida; conferir rollback e inspecionar antes de repetir. ";
			throw new SetupFailure(detail + safeFailure(exception), exception);
		}
		// A conexao dedicada sera fechada; nunca ativar autoCommit para encerrar uma transacao.
	}

	private void verifyCounts(Connection connection) throws SQLException {
		long total = 0;
		boolean matches = true;
		for (var expected : EXPECTED_ROWS.entrySet()) {
			long actual = count(connection, OWNER, expected.getKey());
			total += actual;
			matches &= actual == expected.getValue();
			out.printf("  VALIDACAO: %s encontrado=%d; esperado=%d%n", expected.getKey(), actual, expected.getValue());
		}
		out.println("[Preparacao Oracle] Total encontrado=" + total + "; esperado=132.");
		require(matches && total == 132, "Contagens divergentes do DML: cada tabela e o total devem coincidir.");
	}

	private void runQueries(Connection connection) throws SQLException {
		int index = 0;
		for (String sql : splitTopLevel(withoutComments(bundle.dql()), ';')) {
			require(sql.toUpperCase(Locale.ROOT).startsWith("SELECT "), "DQL contem instrucao nao autorizada.");
			int[] rows = {0};
			query(connection, "DQL consulta " + ++index, sql, result -> rows[0]++);
			out.printf("[Preparacao Oracle] DQL consulta %d: SELECT concluido, %d linha(s).%n", index, rows[0]);
		}
	}

	private static void executeScript(Connection connection, String text, String filename) {
		var resource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8), filename);
		try {
			ScriptUtils.executeSqlScript(connection, new EncodedResource(resource, StandardCharsets.UTF_8),
					false, false, ScriptUtils.DEFAULT_COMMENT_PREFIXES, ";", "/*", "*/");
		} catch (RuntimeException exception) {
			var number = Pattern.compile("statement #(\\d+)").matcher(String.valueOf(exception.getMessage()));
			String statement = number.find() ? number.group(1) : "desconhecida";
			throw new SetupFailure("Arquivo " + filename + ", instrucao " + statement + ": " + safeFailure(exception), exception);
		}
	}

	private long count(Connection connection, String owner, String table) throws SQLException {
		require(EXPECTED_ROWS.containsKey(table), "Tabela nao autorizada para contagem.");
		long[] count = {0};
		int[] rows = {0};
		query(connection, "COUNT " + table, "SELECT COUNT(*) FROM " + quote(owner) + "." + quote(table), result -> {
			count[0] = result.getLong(1);
			require(!result.wasNull() && count[0] >= 0 && ++rows[0] == 1, "Resultado de COUNT(*) inconclusivo.");
		});
		require(rows[0] == 1, "COUNT(*) nao retornou resultado; operacao inconclusiva.");
		return count[0];
	}

	@FunctionalInterface
	private interface RowReader { void read(ResultSet result) throws SQLException; }

	private void query(Connection connection, String label, String sql, RowReader reader) throws SQLException {
		long start = System.nanoTime();
		out.printf("[Preparacao Oracle] Consulta %s: inicio (timeout=%ds).%n", label, QUERY_TIMEOUT_SECONDS);
		try (var statement = connection.createStatement()) {
			statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
			try (var result = statement.executeQuery(sql)) {
				while (result.next()) reader.read(result);
			}
		} catch (SQLException | SetupFailure exception) {
			String message = "Consulta " + label + " INCONCLUSIVA apos " + ((System.nanoTime() - start) / 1_000_000)
					+ " ms; operacao interrompida. " + safeFailure(exception);
			out.println("[Preparacao Oracle] " + message);
			throw new SetupFailure(message, exception);
		}
		out.printf("[Preparacao Oracle] Consulta %s: concluida em %d ms.%n", label, (System.nanoTime() - start) / 1_000_000);
	}

	record Bundle(String ddl, String dml, String dql) {
		static final String DDL_FILE = "scripts/local/01_DDL_EmbarqueCerto_sem_drop.sql";
		static final String DML_FILE = "scripts/local/02_DML_EmbarqueCerto_sem_delete.sql";
		static final String DQL_FILE = "scripts/03_DQL_EmbarqueCerto.sql";

		static Bundle load(Path root) throws Exception {
			return new Bundle(verified(root, DDL_FILE, "8c97537c3dab1fd440eecd80f687a7b7db27098036f07a4d7c149b8fb398b3c1"),
					verified(root, DML_FILE, "ccf80c5b06ac7ed8712819460d9e890eba86f4a627b701c0f98247b7576398d8"),
					verified(root, DQL_FILE, "e5ded82ba7d36f25a44e2fcc14205dd2eb88ed5d15c514320d648825c1175783"));
		}

		private static String verified(Path root, String filename, String hash) throws Exception {
			String text = Files.readString(root.resolve(filename), StandardCharsets.UTF_8)
					.replaceFirst("^\uFEFF", "").replace("\r\n", "\n").strip();
			String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
			require(hash.equals(actual), "Script alterado ou nao aprovado: " + filename + ". Revisar antes de executar.");
			return text;
		}
	}

	/** Manifesto de metadados extraido exclusivamente do DDL local aprovado; nao e entidade JPA. */
	record Schema(Map<String, Map<String, Column>> columns, Map<String, Rule> rules) {
		static Schema parse(String ddl) {
			String sql = withoutComments(ddl);
			var columns = new LinkedHashMap<String, Map<String, Column>>();
			var rules = new LinkedHashMap<String, Rule>();
			var creates = Pattern.compile("(?is)CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.*?)\\)\\s*;").matcher(sql);
			while (creates.find()) {
				String table = creates.group(1).toUpperCase(Locale.ROOT);
				require(EXPECTED_ROWS.containsKey(table), "DDL referencia tabela nao autorizada.");
				var tableColumns = new LinkedHashMap<String, Column>();
				for (String definition : splitTopLevel(creates.group(2), ',')) {
					var constraint = Pattern.compile("(?is)^CONSTRAINT\\s+(\\w+)\\s+(PRIMARY\\s+KEY|UNIQUE|CHECK)\\s*\\((.*)\\)$").matcher(definition);
					if (constraint.matches()) {
						String kind = constraint.group(2).toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
						String type = kind.equals("CHECK") ? "C" : kind.equals("UNIQUE") ? "U" : "P";
						rules.put(constraint.group(1).toUpperCase(Locale.ROOT), new Rule(table, type,
								"C".equals(type) ? List.of() : identifiers(constraint.group(3)),
								"C".equals(type) ? normalize(constraint.group(3)) : null, null, null, List.of()));
					} else {
						var column = Pattern.compile("(?is)^(\\w+)\\s+(NUMBER|VARCHAR2|CHAR|DATE)(?:\\((\\d+)(?:\\s*,\\s*(\\d+))?\\))?(.*)$").matcher(definition);
						require(column.matches(), "Definicao de coluna nao reconhecida no DDL aprovado.");
						String type = column.group(2).toUpperCase(Locale.ROOT);
						String tail = column.group(5).strip();
						boolean nullable = !tail.toUpperCase(Locale.ROOT).endsWith("NOT NULL");
						String defaultValue = tail.replaceFirst("(?is)\\s*NOT\\s+NULL\\s*$", "").strip();
						defaultValue = defaultValue.toUpperCase(Locale.ROOT).startsWith("DEFAULT ") ? normalize(defaultValue.substring(8)) : null;
						Integer size = column.group(3) == null ? null : Integer.valueOf(column.group(3));
						tableColumns.put(column.group(1).toUpperCase(Locale.ROOT), new Column(type,
								"NUMBER".equals(type) ? size : null,
								"NUMBER".equals(type) ? column.group(4) == null ? 0 : Integer.valueOf(column.group(4)) : null,
								Set.of("CHAR", "VARCHAR2").contains(type) ? size : null, nullable, defaultValue));
					}
				}
				columns.put(table, Collections.unmodifiableMap(tableColumns));
			}
			var keys = Pattern.compile("(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+CONSTRAINT\\s+(\\w+)\\s+FOREIGN\\s+KEY\\s*\\((.*?)\\)\\s+REFERENCES\\s+(\\w+)\\s*\\((.*?)\\)\\s*;").matcher(sql);
			while (keys.find()) {
				rules.put(keys.group(2).toUpperCase(Locale.ROOT), new Rule(keys.group(1).toUpperCase(Locale.ROOT), "R",
						identifiers(keys.group(3)), null, OWNER, keys.group(4).toUpperCase(Locale.ROOT), identifiers(keys.group(5))));
			}
			require(columns.keySet().equals(EXPECTED_ROWS.keySet()) && columns.values().stream().mapToInt(Map::size).sum() == 65
					&& rules.size() == 61, "Manifesto incompleto: revisar parser e DDL antes de executar.");
			return new Schema(Collections.unmodifiableMap(columns), Collections.unmodifiableMap(rules));
		}
	}

	static List<String> splitTopLevel(String text, char separator) {
		var parts = new ArrayList<String>();
		var part = new StringBuilder();
		int depth = 0;
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char value = text.charAt(i);
			if (value == '\'') {
				part.append(value);
				if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '\'') part.append(text.charAt(++i));
				else quoted = !quoted;
				continue;
			}
			if (!quoted) {
				if (value == '(') depth++;
				if (value == ')') depth--;
				if (value == separator && depth == 0) {
					if (!part.toString().isBlank()) parts.add(part.toString().strip());
					part.setLength(0);
					continue;
				}
			}
			part.append(value);
		}
		require(!quoted && depth == 0, "SQL com aspas ou parenteses incompletos.");
		if (!part.toString().isBlank()) parts.add(part.toString().strip());
		return parts;
	}

	static String withoutComments(String sql) {
		// Os arquivos aprovados usam apenas comentarios em linhas completas.
		return sql.replaceAll("(?m)^\\s*--[^\\r\\n]*(?:\\r?\\n|$)", "");
	}

	static String normalize(String value) {
		if (value == null || value.isBlank()) return null;
		var normalized = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character == '\'') {
				normalized.append(character);
				if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') normalized.append(value.charAt(++i));
				else quoted = !quoted;
			} else if (quoted) normalized.append(character);
			else if (!Character.isWhitespace(character) && character != '"') normalized.append(Character.toUpperCase(character));
		}
		return normalized.toString();
	}

	private static List<String> identifiers(String value) {
		return splitTopLevel(value, ',').stream().map(String::strip).map(name -> name.toUpperCase(Locale.ROOT)).toList();
	}

	private static Integer integer(ResultSet result, String name) throws SQLException {
		int value = result.getInt(name);
		return result.wasNull() ? null : value;
	}

	private static String quote(String name) { return "\"" + name.replace("\"", "\"\"") + "\""; }
	private static boolean blank(String value) { return value == null || value.isBlank(); }
	private static void require(boolean condition, String message) { if (!condition) throw new SetupFailure(message); }

	private static String names(Set<String> names) {
		return String.join(",", names.stream().map(name -> {
			require(name.matches("[A-Z][A-Z0-9_]*"), "Identificador nao autorizado.");
			return "'" + name + "'";
		}).toList());
	}

	static String safeFailure(Throwable exception) {
		if (exception instanceof SetupFailure) return exception.getMessage();
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql) {
				String reason = sql instanceof SQLTimeoutException ? "timeout JDBC da consulta" : switch (sql.getErrorCode()) {
					case 1 -> "chave primaria/unica duplicada";
					case 1013 -> "consulta cancelada (possivel timeout JDBC ou cancelamento solicitado)";
					case 1017 -> "autenticacao recusada";
					case 1031 -> "permissao insuficiente";
					case 1950 -> "quota de tablespace insuficiente";
					case 955 -> "nome de objeto ja existente";
					case 2291 -> "chave estrangeira sem registro pai";
					default -> "falha JDBC; consultar o codigo Oracle";
				};
				String code = sql.getErrorCode() == 0 ? "Codigo JDBC=0"
						: "ORA-" + String.format(Locale.ROOT, "%05d", Math.abs(sql.getErrorCode()));
				return code
						+ "; SQLState=" + sql.getSQLState() + "; " + reason;
			}
		}
		// Nao exibir mensagens de drivers/configuracao que possam conter valores de conexao.
		return exception.getClass().getSimpleName() + "; verificar configuracao existente sem divulgar credenciais.";
	}

	private static Map<String, Long> expectedRows() {
		var rows = new LinkedHashMap<String, Long>();
		String[] tables = {"OPERADORA", "LINHA", "ESTACAO", "LINHA_ESTACAO", "USUARIO", "TIPO_OCORRENCIA",
				"OCORRENCIA", "CONFIRMACAO", "REGISTRO_LOTACAO", "TRAJETO", "ALERTA"};
		long[] counts = {5, 6, 15, 22, 8, 6, 12, 17, 20, 8, 13};
		for (int i = 0; i < tables.length; i++) rows.put("T_EC_" + tables[i], counts[i]);
		return Collections.unmodifiableMap(rows);
	}
}
