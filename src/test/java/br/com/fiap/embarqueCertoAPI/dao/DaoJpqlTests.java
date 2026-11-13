package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.*;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Compila JPQL e seus parametros sem executar consultas ou obter conexoes. */
class DaoJpqlTests {

    private static final List<Class<?>> DAOS = List.of(EstacaoDAO.class, LinhaDAO.class,
            OcorrenciaDAO.class, RegistroLotacaoDAO.class);
    private static final RejectingConnections connections = new RejectingConnections();
    private static StandardServiceRegistry registry;
    private static SessionFactory factory;

    record Consulta(String nome, String jpql, Class<?> resultado) {
        @Override
        public String toString() {
            return nome;
        }
    }

    @BeforeAll
    static void startHibernateWithoutJdbc() {
        registry = new StandardServiceRegistryBuilder()
                .addService(ConnectionProvider.class, connections)
                .applySetting("hibernate.dialect", OracleDialect.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "none")
                .applySetting("jakarta.persistence.validation.mode", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            List.of(Operadora.class, Linha.class, Estacao.class, LinhaEstacao.class, Usuario.class,
                    TipoOcorrencia.class, Ocorrencia.class, Confirmacao.class, RegistroLotacao.class,
                    Trajeto.class, Alerta.class).forEach(sources::addAnnotatedClass);
            factory = sources.buildMetadata().buildSessionFactory();
        } catch (Throwable failure) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw failure;
        }
    }

    @AfterAll
    static void stopHibernate() {
        try {
            if (factory != null) {
                factory.close();
            }
        } finally {
            if (registry != null) {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
        assertThat(connections.attempts.get()).isZero();
    }

    static Stream<Consulta> consultas() {
        return DAOS.stream().flatMap(dao -> Stream.of(dao.getDeclaredFields())
                .filter(f -> Modifier.isStatic(f.getModifiers()) && f.getName().endsWith("_JPQL"))
                .map(field -> {
                    try {
                        Class<?> result = field.getName().equals("LISTAR_JPQL")
                                ? Class.forName("br.com.fiap.embarqueCertoAPI.model."
                                        + dao.getSimpleName().replace("DAO", "")) : Long.class;
                        return new Consulta(dao.getSimpleName() + "." + field.getName(),
                                (String) field.get(null), result);
                    } catch (ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                }));
    }

    @Test
    void coversAllFifteenSelectionQueries() {
        assertThat(consultas().count()).isEqualTo(15);
    }

    @ParameterizedTest
    @MethodSource("consultas")
    void compilesEntityPropertiesResultTypesAndParameters(Consulta consulta) {
        try (var entityManager = factory.createEntityManager()) {
            var query = entityManager.createQuery(consulta.jpql(), consulta.resultado());
            for (var parameter : query.getParameters()) {
                Class<?> type = parameter.getParameterType();
                assertThat(type).isIn(String.class, Integer.class, Long.class);
                Object value;
                if (type == String.class) {
                    value = "Nome ' parametrizado";
                } else if (type == Integer.class) {
                    value = Integer.valueOf(1);
                } else {
                    value = Long.valueOf(1);
                }
                query.setParameter(parameter.getName(), value);
            }
            if (consulta.jpql().contains(":idIgnorado")) {
                assertThat(query.getParameter("idIgnorado").getParameterType()).isEqualTo(Long.class);
                query.setParameter("idIgnorado", null);
            }
            // Nao chamar getResultList/getSingleResult: nao ha banco neste teste.
        }
        assertThat(connections.attempts.get()).isZero();
    }

    private static final class RejectingConnections implements ConnectionProvider {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Connection getConnection() {
            attempts.incrementAndGet();
            throw new AssertionError("Conexao JDBC proibida na compilacao JPQL");
        }

        @Override
        public void closeConnection(Connection connection) {
            throw new AssertionError("Nenhuma conexao deveria existir");
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> type) {
            return type.isInstance(this);
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            return type.cast(this);
        }
    }
}
