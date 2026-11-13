package br.com.fiap.embarqueCertoAPI;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O contexto e o health sao exercitados sem abrir conexao JDBC. */
@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.jpa.database-platform=org.hibernate.dialect.OracleDialect",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
		"spring.jpa.properties.jakarta.persistence.schema-generation.database.action=none",
		"spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=none",
		"spring.sql.init.mode=never",
		"diagnostico.oracle.enabled=false"
})
@AutoConfigureMockMvc
class EmbarqueCertoApiApplicationTests {

	@MockitoBean
	private DataSource dataSource;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() throws SQLException {
		assertNoJdbcConnection();
	}

	@Test
	void healthRemainsAvailableWithoutDatabaseAccess() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.aplicacao").value("Embarque Certo API"));
		assertNoJdbcConnection();
	}

	private void assertNoJdbcConnection() throws SQLException {
		verify(dataSource, never()).getConnection();
		verify(dataSource, never()).getConnection(any(), any());
	}

}
