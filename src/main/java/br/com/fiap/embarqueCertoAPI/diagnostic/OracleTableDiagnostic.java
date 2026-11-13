package br.com.fiap.embarqueCertoAPI.diagnostic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Diagnostico local temporario, executado somente quando habilitado explicitamente. */
@Component
@ConditionalOnProperty(prefix = "diagnostico.oracle", name = "enabled", havingValue = "true")
public class OracleTableDiagnostic implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(OracleTableDiagnostic.class);

	private static final String TABLES_QUERY =
			"SELECT table_name FROM user_tables WHERE table_name LIKE 'T_EC_%' ORDER BY table_name";

	private static final String SESSION_QUERY = "SELECT USER AS usuario, "
			+ "SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS schema_atual FROM dual";

	private static final String ALL_TABLES_QUERY = "SELECT owner, table_name FROM all_tables "
			+ "WHERE table_name LIKE 'T_EC_%' ORDER BY owner, table_name";

	// Lista fixa do DDL e quantidades previstas no DML, informadas para o projeto.
	private static final List<ExpectedTable> EXPECTED_TABLES = List.of(
			new ExpectedTable("T_EC_OPERADORA", 5),
			new ExpectedTable("T_EC_LINHA", 6),
			new ExpectedTable("T_EC_ESTACAO", 15),
			new ExpectedTable("T_EC_LINHA_ESTACAO", 22),
			new ExpectedTable("T_EC_USUARIO", 8),
			new ExpectedTable("T_EC_TIPO_OCORRENCIA", 6),
			new ExpectedTable("T_EC_OCORRENCIA", 12),
			new ExpectedTable("T_EC_CONFIRMACAO", 17),
			new ExpectedTable("T_EC_REGISTRO_LOTACAO", 20),
			new ExpectedTable("T_EC_TRAJETO", 8),
			new ExpectedTable("T_EC_ALERTA", 13));

	private record ExpectedTable(String name, long expectedRows) {
	}

	private record SessionIdentity(String user, String currentSchema) {
	}

	private record OwnedTable(String owner, String name) {
	}

	private final DataSource dataSource;

	public OracleTableDiagnostic(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void run(ApplicationArguments args) {
		LOGGER.info("[Diagnostico Oracle] Iniciando verificacao temporaria; somente SELECT.");
		try (var connection = dataSource.getConnection()) {
			var session = readSession(connection);
			var userTables = readUserTables(connection);
			var allTables = readAllTables(connection);
			userTables.ifPresent(tables -> diagnoseOwnTables(connection, tables, session));
			compareTablesAndAccess(connection, session, userTables, allTables);
		} catch (SQLException exception) {
			reportSqlError("obter ou encerrar a conexao", exception);
		}
		LOGGER.info("[Diagnostico Oracle] Verificacao temporaria encerrada.");
	}

	private void diagnoseOwnTables(Connection connection, List<String> foundTables,
			Optional<SessionIdentity> session) {
		foundTables.forEach(table -> LOGGER.info("[Diagnostico Oracle] Tabela encontrada: {}", table));
		LOGGER.info("[Diagnostico Oracle] Total de tabelas encontradas: {}", foundTables.size());

		for (var table : foundTables) {
			if (EXPECTED_TABLES.stream().noneMatch(expected -> expected.name().equals(table))) {
				LOGGER.warn("[Diagnostico Oracle] {} esta fora da lista fixa; contagem ignorada.", table);
			}
		}

		if (session.isEmpty()) {
			LOGGER.warn("[Diagnostico Oracle] Identidade da sessao indisponivel; contagens de USER_TABLES "
					+ "nao executadas para evitar consultar outro schema.");
			return;
		}

		int presentTables = 0;
		int countedTables = 0;
		int divergentTables = 0;
		long totalRows = 0;
		for (var expectedTable : EXPECTED_TABLES) {
			if (foundTables.contains(expectedTable.name())) {
				presentTables++;
				var identity = session.get();
				String target = identity.user().equals(identity.currentSchema()) ? expectedTable.name()
						: qualifiedName(identity.user(), expectedTable.name());
				var count = countRows(connection, expectedTable, target);
				if (count.isPresent()) {
					countedTables++;
					totalRows += count.getAsLong();
					if (count.getAsLong() != expectedTable.expectedRows()) {
						divergentTables++;
					}
				}
			} else {
				LOGGER.warn("[Diagnostico Oracle] Tabela esperada nao encontrada em USER_TABLES: {}",
						expectedTable.name());
			}
		}

		LOGGER.info("[Diagnostico Oracle] Tabelas autorizadas: presentes={}, ausentes={}, total esperado={}.",
				presentTables, EXPECTED_TABLES.size() - presentTables, EXPECTED_TABLES.size());
		LOGGER.info("[Diagnostico Oracle] Contagens concluidas={}, divergentes={}.",
				countedTables, divergentTables);
		long expectedTotal = EXPECTED_TABLES.stream().mapToLong(ExpectedTable::expectedRows).sum();
		if (countedTables == EXPECTED_TABLES.size()) {
			LOGGER.info("[Diagnostico Oracle] Total de registros das 11 tabelas: encontrado={}, esperado no DML={}.",
					totalRows, expectedTotal);
		} else {
			LOGGER.warn("[Diagnostico Oracle] Comparacao total incompleta: contagens concluidas={}/{}, "
					+ "subtotal encontrado={}, total esperado no DML={}.",
					countedTables, EXPECTED_TABLES.size(), totalRows, expectedTotal);
		}
	}

	private Optional<SessionIdentity> readSession(Connection connection) {
		try (var statement = connection.createStatement()) {
			statement.setQueryTimeout(30);
			try (var result = statement.executeQuery(SESSION_QUERY)) {
				if (result.next()) {
					var identity = new SessionIdentity(result.getString("USUARIO"), result.getString("SCHEMA_ATUAL"));
					LOGGER.info("[Diagnostico Oracle] Sessao: USER={}, CURRENT_SCHEMA={}.",
							identity.user(), identity.currentSchema());
					if (!identity.user().equals(identity.currentSchema())) {
						LOGGER.warn("[Diagnostico Oracle] CURRENT_SCHEMA difere de USER. USER_TABLES descreve "
								+ "o usuario conectado; suas contagens usarao esse USER como OWNER explicito.");
					}
					return Optional.of(identity);
				}
				LOGGER.error("[Diagnostico Oracle] A consulta de identidade da sessao nao retornou resultado.");
			}
		} catch (SQLException exception) {
			reportSqlError("identificar USER e CURRENT_SCHEMA", exception);
		}
		return Optional.empty();
	}

	private Optional<List<String>> readUserTables(Connection connection) {
		try {
			return Optional.of(findTables(connection));
		} catch (SQLException exception) {
			reportSqlError("consultar USER_TABLES", exception);
			return Optional.empty();
		}
	}

	private Optional<List<OwnedTable>> readAllTables(Connection connection) {
		try (var statement = connection.createStatement()) {
			statement.setQueryTimeout(30);
			var tables = new ArrayList<OwnedTable>();
			try (var result = statement.executeQuery(ALL_TABLES_QUERY)) {
				while (result.next()) {
					var table = new OwnedTable(result.getString("OWNER"), result.getString("TABLE_NAME"));
					tables.add(table);
					LOGGER.info("[Diagnostico Oracle] ALL_TABLES: OWNER={}, TABLE_NAME={}.",
							table.owner(), table.name());
				}
			}
			LOGGER.info("[Diagnostico Oracle] Total de objetos encontrados em ALL_TABLES: {}", tables.size());
			return Optional.of(tables);
		} catch (SQLException exception) {
			reportSqlError("consultar ALL_TABLES", exception);
			return Optional.empty();
		}
	}

	private void compareTablesAndAccess(Connection connection, Optional<SessionIdentity> session,
			Optional<List<String>> userTables, Optional<List<OwnedTable>> allTables) {
		if (allTables.isEmpty()) {
			LOGGER.warn("[Diagnostico Oracle] Comparacao USER_TABLES/ALL_TABLES incompleta: ALL_TABLES "
					+ "indisponivel. Acessibilidade das 11 tabelas via esse catalogo INDETERMINADA.");
			return;
		}

		var confirmedByOwner = new LinkedHashMap<String, Integer>();
		int visibleNames = 0;
		int readableNames = 0;
		for (var expected : EXPECTED_TABLES) {
			var matches = allTables.get().stream().filter(table -> expected.name().equals(table.name())).toList();
			String ownedStatus = userTables.map(tables -> tables.contains(expected.name()) ? "PRESENTE" : "AUSENTE")
					.orElse("INDETERMINADO");
			LOGGER.info("[Diagnostico Oracle] Comparacao {}: USER_TABLES={}, ALL_TABLES OWNER(s)={}.",
					expected.name(), ownedStatus, matches.stream().map(OwnedTable::owner).toList());
			if (!matches.isEmpty()) {
				visibleNames++;
			}
			boolean readable = false;
			for (var table : matches) {
				confirmedByOwner.putIfAbsent(table.owner(), 0);
				if (canSelect(connection, table)) {
					readable = true;
					confirmedByOwner.merge(table.owner(), 1, Integer::sum);
				}
			}
			if (readable) {
				readableNames++;
			} else {
				LOGGER.warn("[Diagnostico Oracle] SELECT nao confirmado para {} via objetos de ALL_TABLES.",
						expected.name());
			}
		}

		LOGGER.info("[Diagnostico Oracle] ALL_TABLES: nomes autorizados visiveis={}/11; "
				+ "nomes com SELECT confirmado={}/11.", visibleNames, readableNames);
		confirmedByOwner.forEach((owner, count) -> LOGGER.info(
				"[Diagnostico Oracle] OWNER={}: tabelas autorizadas com SELECT confirmado={}/11.", owner, count));
		if (readableNames == EXPECTED_TABLES.size()) {
			LOGGER.info("[Diagnostico Oracle] Os 11 nomes autorizados estao acessiveis por SELECT com OWNER explicito.");
			if (confirmedByOwner.values().stream().noneMatch(count -> count == EXPECTED_TABLES.size())) {
				LOGGER.warn("[Diagnostico Oracle] Os 11 nomes estao distribuidos entre OWNERs; "
						+ "nenhum OWNER teve o conjunto completo de 11 tabelas confirmado.");
			}
		} else {
			LOGGER.warn("[Diagnostico Oracle] Acesso SELECT ao conjunto de 11 tabelas NAO TOTALMENTE CONFIRMADO.");
		}
		session.ifPresent(identity -> LOGGER.info("[Diagnostico Oracle] CURRENT_SCHEMA={}: "
				+ "SELECT por OWNER explicito confirmado para {}/11 tabelas. Sinonimos nao sao avaliados.",
				identity.currentSchema(), confirmedByOwner.getOrDefault(identity.currentSchema(), 0)));

		if (allTables.get().isEmpty()) {
			LOGGER.warn("[Diagnostico Oracle] Nenhuma tabela T_EC_% encontrada em ALL_TABLES nesta sessao.");
			userTables.ifPresent(tables -> LOGGER.warn("[Diagnostico Oracle] Comparacao dos catalogos: "
					+ "USER_TABLES={}, ALL_TABLES=0.", tables.size()));
			LOGGER.warn("[Diagnostico Oracle] Possiveis causas, nao confirmadas: servico/instancia/PDB diferente "
					+ "entre as conexoes; objetos em outro OWNER sem visibilidade/permissao; nomes com caixa "
					+ "diferente criados entre aspas; objetos que sejam visoes/sinonimos; alteracoes posteriores.");
			LOGGER.warn("[Diagnostico Oracle] Ausencia nesses catalogos nao prova que o DDL nao foi executado.");
		}
	}

	private boolean canSelect(Connection connection, OwnedTable table) {
		// Somente nomes da lista fixa chegam aqui. O OWNER do catalogo e escapado como identificador.
		String query = "SELECT 1 FROM " + qualifiedName(table.owner(), table.name()) + " WHERE 1 = 0";
		try (var statement = connection.createStatement()) {
			statement.setQueryTimeout(30);
			try (var result = statement.executeQuery(query)) {
				result.next();
			}
		} catch (SQLException exception) {
			reportSqlError("verificar SELECT de " + table.owner() + "." + table.name(), exception);
			return false;
		}
		LOGGER.info("[Diagnostico Oracle] SELECT confirmado: OWNER={}, TABLE_NAME={}.", table.owner(), table.name());
		return true;
	}

	private String qualifiedName(String owner, String table) {
		return "\"" + owner.replace("\"", "\"\"") + "\".\"" + table.replace("\"", "\"\"") + "\"";
	}

	private List<String> findTables(Connection connection) throws SQLException {
		var tables = new ArrayList<String>();
		try (var statement = connection.createStatement()) {
			statement.setQueryTimeout(30);
			try (var result = statement.executeQuery(TABLES_QUERY)) {
				while (result.next()) {
					tables.add(result.getString("TABLE_NAME"));
				}
			}
		}
		return tables;
	}

	private OptionalLong countRows(Connection connection, ExpectedTable expectedTable, String target) {
		try (var statement = connection.createStatement()) {
			statement.setQueryTimeout(30);
			// O nome vem exclusivamente da constante EXPECTED_TABLES, nunca de entrada externa.
			try (var result = statement.executeQuery("SELECT COUNT(*) FROM " + target)) {
				if (result.next()) {
					long count = result.getLong(1);
					if (count == expectedTable.expectedRows()) {
						LOGGER.info("[Diagnostico Oracle] {}: encontrado={}, esperado={} [OK].",
								expectedTable.name(), count, expectedTable.expectedRows());
					} else {
						LOGGER.warn("[Diagnostico Oracle] {}: encontrado={}, esperado={} [DIVERGENCIA].",
								expectedTable.name(), count, expectedTable.expectedRows());
					}
					return OptionalLong.of(count);
				}
				LOGGER.error("[Diagnostico Oracle] COUNT(*) de {} nao retornou resultado; contagem desconhecida.",
						expectedTable.name());
			}
		} catch (SQLException exception) {
			reportSqlError("contar registros de " + expectedTable.name(), exception);
		}
		return OptionalLong.empty();
	}

	private void reportSqlError(String operation, SQLException exception) {
		for (var error = exception; error != null; error = error.getNextException()) {
			LOGGER.error("[Diagnostico Oracle] Falha ao {}. Codigo Oracle={}, SQLState={}, detalhe={}",
					operation, error.getErrorCode(), error.getSQLState(), error.getMessage());
			if (error.getErrorCode() == 1031) {
				LOGGER.error("[Diagnostico Oracle] Permissao insuficiente para executar a consulta (ORA-01031).");
			} else if (error.getErrorCode() == 942) {
				LOGGER.error("[Diagnostico Oracle] Tabela/visao inexistente ou sem permissao SELECT (ORA-00942).");
			}
		}
	}

}
