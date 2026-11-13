package br.com.fiap.embarqueCertoAPI.model;

import br.com.fiap.embarqueCertoAPI.model.id.AlertaId;
import br.com.fiap.embarqueCertoAPI.model.id.ConfirmacaoId;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.type.BasicType;
import org.hibernate.type.descriptor.WrapperOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Le somente o texto do DDL e constroi o Hibernate com todo acesso JDBC proibido. */
class ModelMappingTests {

    private static final List<Class<?>> ENTITIES = List.of(Operadora.class, Linha.class, Estacao.class,
            LinhaEstacao.class, Usuario.class, TipoOcorrencia.class, Ocorrencia.class, Confirmacao.class,
            RegistroLotacao.class, Trajeto.class, Alerta.class);
    private static final List<Class<?>> KEYS = List.of(LinhaEstacaoId.class, ConfirmacaoId.class, AlertaId.class);
    private static final RejectingConnectionProvider CONNECTIONS = new RejectingConnectionProvider();
    private static StandardServiceRegistry registry;
    private static MetadataImplementor metadata;
    private static SessionFactory sessionFactory;
    private static Map<String, TableSpec> ddlTables;
    private static Map<String, ForeignKeySpec> ddlForeignKeys;

    record ColumnSpec(String name, String sqlType, boolean nullable) {}
    record TableSpec(String name, Map<String, ColumnSpec> columns, List<String> primaryKey,
                     Map<String, List<String>> uniqueKeys) {}
    record ForeignKeySpec(String table, List<String> columns, String target, List<String> targetColumns) {}

    @BeforeAll
    static void buildOfflineMetadata() throws Exception {
        readDdl();
        registry = new StandardServiceRegistryBuilder()
                .addService(ConnectionProvider.class, CONNECTIONS)
                .applySetting("hibernate.dialect", OracleDialect.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                .applySetting("jakarta.persistence.schema-generation.scripts.action", "none")
                .applySetting("jakarta.persistence.validation.mode", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            ENTITIES.forEach(sources::addAnnotatedClass);
            metadata = (MetadataImplementor) sources.buildMetadata();
            metadata.validate();
            // A fabrica verifica tambem colunas gravaveis duplicadas e os @MapsId.
            sessionFactory = metadata.buildSessionFactory();
        } catch (Throwable error) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw error;
        }
    }

    @AfterAll
    static void closeOfflineMetadata() {
        try {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
        } finally {
            if (registry != null) {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
        assertThat(CONNECTIONS.attempts.get()).as("Nenhuma tentativa de conexao JDBC").isZero();
    }

    private static void readDdl() throws Exception {
        String ddl = Files.readString(Path.of("scripts/01_DDL_EmbarqueCerto.sql"))
                .replaceAll("(?m)--[^\\r\\n]*", "").toUpperCase(Locale.ROOT);
        ddlTables = new LinkedHashMap<>();
        var tables = Pattern.compile("(?s)CREATE TABLE\\s+(\\w+)\\s*\\((.*?)\\);").matcher(ddl);
        while (tables.find()) {
            String name = tables.group(1);
            String body = tables.group(2);
            Map<String, ColumnSpec> columns = new LinkedHashMap<>();
            var column = Pattern.compile("(?m)^\\s*(\\w+)\\s+"
                    + "(NUMBER\\(\\d+(?:,\\d+)?\\)|VARCHAR2\\(\\d+\\)|CHAR\\(\\d+\\)|DATE)"
                    + "([^\\r\\n]*)").matcher(body);
            while (column.find()) {
                columns.put(column.group(1), new ColumnSpec(column.group(1), column.group(2),
                        !column.group(3).contains("NOT NULL")));
            }
            var pk = Pattern.compile("PRIMARY KEY\\s*\\(([^)]+)\\)").matcher(body);
            assertThat(pk.find()).as("PK de " + name).isTrue();
            Map<String, List<String>> uniqueKeys = new LinkedHashMap<>();
            var unique = Pattern.compile("CONSTRAINT\\s+(\\w+)\\s+UNIQUE\\s*\\(([^)]+)\\)").matcher(body);
            while (unique.find()) {
                uniqueKeys.put(unique.group(1), names(unique.group(2)));
            }
            ddlTables.put(name, new TableSpec(name, columns, names(pk.group(1)), uniqueKeys));
        }
        ddlForeignKeys = new LinkedHashMap<>();
        var foreignKey = Pattern.compile("ALTER TABLE\\s+(\\w+)\\s+ADD CONSTRAINT\\s+(\\w+)\\s+"
                + "FOREIGN KEY\\s*\\(([^)]+)\\)\\s+REFERENCES\\s+(\\w+)\\s*\\(([^)]+)\\)").matcher(ddl);
        while (foreignKey.find()) {
            ddlForeignKeys.put(foreignKey.group(2), new ForeignKeySpec(foreignKey.group(1),
                    names(foreignKey.group(3)), foreignKey.group(4), names(foreignKey.group(5))));
        }
        // Impede que uma mudanca no formato do SQL reduza silenciosamente a cobertura.
        assertThat(ddlTables).hasSize(11);
        assertThat(ddlTables.values().stream().mapToInt(t -> t.columns().size()).sum()).isEqualTo(65);
        assertThat(ddlTables.values().stream().mapToInt(t -> t.uniqueKeys().size()).sum()).isEqualTo(8);
        assertThat(ddlForeignKeys).hasSize(17);
    }

    private static List<String> names(String columns) {
        return Arrays.stream(columns.split(",")).map(String::trim).toList();
    }

    private static List<String> names(Collection<Column> columns) {
        return columns.stream().map(Column::getName).map(n -> n.toUpperCase(Locale.ROOT)).toList();
    }

    static Stream<TableSpec> tables() {
        return ddlTables.values().stream();
    }

    static Stream<Class<?>> entities() {
        return ENTITIES.stream();
    }

    static Stream<Class<?>> keys() {
        return KEYS.stream();
    }

    private static org.hibernate.mapping.Table table(String name) {
        return metadata.getEntityBindings().stream().map(binding -> binding.getTable())
                .filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void registersExactlyElevenEntitiesWithoutCollectionsOrSequences() {
        assertThat(metadata.getEntityBindings()).hasSize(11);
        assertThat(metadata.getCollectionBindings()).isEmpty();
        assertThat(metadata.getDatabase().getNamespaces()).allSatisfy(namespace ->
                assertThat(namespace.getSequences()).isEmpty());
        assertThat(CONNECTIONS.attempts.get()).isZero();
    }

    @ParameterizedTest
    @MethodSource("tables")
    void matchesEveryPhysicalColumnAndPrimaryKeyFromDdl(TableSpec expected) {
        var actual = table(expected.name());
        assertThat(actual.getSchema()).isNull();
        assertThat(actual.getCatalog()).isNull();
        assertThat(names(actual.getColumns())).containsExactlyInAnyOrderElementsOf(expected.columns().keySet());
        assertThat(names(actual.getPrimaryKey().getColumns())).containsExactlyInAnyOrderElementsOf(expected.primaryKey());
        for (Column column : actual.getColumns()) {
            var spec = expected.columns().get(column.getName());
            assertThat(column.isNullable()).as(expected.name() + "." + column.getName()).isEqualTo(spec.nullable());
            assertThat(column.getSqlType(metadata).replace(" ", "").toUpperCase(Locale.ROOT)).isEqualTo(spec.sqlType());
            if (spec.sqlType().startsWith("NUMBER")) {
                String[] size = spec.sqlType().replace("NUMBER(", "").replace(")", "").split(",");
                int precision = Integer.parseInt(size[0]);
                int scale = size.length == 1 ? 0 : Integer.parseInt(size[1]);
                if (column.getPrecision() == null) {
                    // @MapsId usa o JoinColumn, cuja API JPA nao possui precision/scale.
                    // O NUMBER(p) fisico ja foi conferido acima; conferimos tambem o ID escalar.
                    assertThat(expected.primaryKey()).hasSize(2).contains(column.getName());
                    var binding = metadata.getEntityBindings().stream()
                            .filter(b -> b.getTable() == actual).findFirst().orElseThrow();
                    var idType = binding.getIdentifier().getType().getReturnedClass();
                    var idColumn = Arrays.stream(idType.getDeclaredFields())
                            .map(f -> f.getAnnotation(jakarta.persistence.Column.class))
                            .filter(c -> c != null && c.name().equals(column.getName())).findFirst().orElseThrow();
                    assertThat(idColumn.precision()).isEqualTo(precision);
                    assertThat(idColumn.scale()).isEqualTo(scale);
                } else {
                    assertThat(column.getPrecision()).as(column.getName() + " precision").isEqualTo(precision);
                    assertThat(column.getScale()).as(column.getName() + " scale").isEqualTo(scale);
                }
                assertThat(column.getSqlTypeCode(metadata)).isEqualTo(Types.NUMERIC);
            } else if (spec.sqlType().startsWith("VARCHAR2") || spec.sqlType().startsWith("CHAR")) {
                int open = spec.sqlType().indexOf('(');
                int length = Integer.parseInt(spec.sqlType().substring(open + 1, spec.sqlType().length() - 1));
                assertThat(column.getLength()).isEqualTo((long) length);
                assertThat(column.getSqlTypeCode(metadata))
                        .isEqualTo(spec.sqlType().startsWith("CHAR") ? Types.CHAR : Types.VARCHAR);
            } else {
                // DATE fisico, com acesso JDBC por timestamp para manter horas/minutos/segundos.
                assertThat(column.getSqlTypeCode(metadata)).isEqualTo(Types.TIMESTAMP);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("tables")
    void matchesAllNamedUniqueConstraintsFromDdl(TableSpec expected) {
        Map<String, List<String>> actual = new LinkedHashMap<>();
        table(expected.name()).getUniqueKeys().forEach((name, key) -> actual.put(name, names(key.getColumns())));
        assertThat(actual).isEqualTo(expected.uniqueKeys());
    }

    @Test
    void matchesAllSeventeenNamedForeignKeysAndTheirTargets() {
        Map<String, ForeignKeySpec> actual = new LinkedHashMap<>();
        for (var binding : metadata.getEntityBindings()) {
            for (ForeignKey fk : binding.getTable().getForeignKeyCollection()) {
                var targetColumns = fk.isReferenceToPrimaryKey()
                        ? fk.getReferencedTable().getPrimaryKey().getColumns() : fk.getReferencedColumns();
                actual.put(fk.getName(), new ForeignKeySpec(binding.getTable().getName(), names(fk.getColumns()),
                        fk.getReferencedTable().getName(), names(targetColumns)));
            }
        }
        assertThat(actual).hasSize(17).isEqualTo(ddlForeignKeys);
    }

    @ParameterizedTest
    @MethodSource("entities")
    void entitiesUseFieldIdsLazyRelationsAndNoCascades(Class<?> type) throws Exception {
        assertThat(Modifier.isFinal(type.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(type.getConstructor().getModifiers())).isTrue();
        assertThat(type.getAnnotation(jakarta.persistence.Table.class).schema()).isEmpty();
        long ids = 0;
        for (var field : type.getDeclaredFields()) {
            assertThat(field.isAnnotationPresent(GeneratedValue.class)).isFalse();
            if (field.isAnnotationPresent(Id.class)) {
                ids++;
                assertThat(field.getType()).isEqualTo(Long.class);
            }
            if (field.isAnnotationPresent(EmbeddedId.class)) {
                ids++;
                assertThat(field.isAnnotationPresent(Valid.class)).isTrue();
            }
            var relation = field.getAnnotation(ManyToOne.class);
            if (relation != null) {
                assertThat(relation.fetch()).isEqualTo(FetchType.LAZY);
                assertThat(relation.cascade()).isEmpty();
                assertThat(field.isAnnotationPresent(Valid.class)).isFalse();
                boolean optional = type == Ocorrencia.class
                        && (field.getName().equals("estacao") || field.getName().equals("usuario"));
                assertThat(relation.optional()).isEqualTo(optional);
                assertThat(field.isAnnotationPresent(NotNull.class)).isEqualTo(!optional);
                assertThat(field.getAnnotation(jakarta.persistence.JoinColumn.class).nullable()).isEqualTo(optional);
            }
        }
        assertThat(ids).isEqualTo(1);
        for (var method : type.getDeclaredMethods()) {
            assertThat(method.isAnnotationPresent(PrePersist.class)).isFalse();
            assertThat(method.isAnnotationPresent(PreUpdate.class)).isFalse();
            assertThat(method.isAnnotationPresent(PreRemove.class)).isFalse();
            assertThat(method.getName()).isNotIn("equals", "hashCode", "toString");
        }
    }

    @ParameterizedTest
    @MethodSource("keys")
    void embeddedKeysContainOnlyScalarLongComponents(Class<?> type) throws Exception {
        assertThat(type.isAnnotationPresent(jakarta.persistence.Embeddable.class)).isTrue();
        assertThat(java.io.Serializable.class).isAssignableFrom(type);
        assertThat(Modifier.isFinal(type.getModifiers())).isFalse();
        type.getConstructor();
        assertThat(Arrays.stream(type.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())))
                .hasSize(2).allSatisfy(field -> {
                    assertThat(field.getType()).isEqualTo(Long.class);
                    assertThat(field.getAnnotation(ManyToOne.class)).isNull();
                    assertThat(field.getAnnotation(NotNull.class)).isNotNull();
                });
    }

    @Test
    void mapsEachCompositeIdComponentToItsRelationship() throws Exception {
        assertMapsId(LinhaEstacao.class, "linha", "idLinha");
        assertMapsId(LinhaEstacao.class, "estacao", "idEstacao");
        assertMapsId(Confirmacao.class, "ocorrencia", "idOcorrencia");
        assertMapsId(Confirmacao.class, "usuario", "idUsuario");
        assertMapsId(Alerta.class, "trajeto", "idTrajeto");
        assertMapsId(Alerta.class, "ocorrencia", "idOcorrencia");
    }

    private void assertMapsId(Class<?> entity, String relationship, String idComponent) throws Exception {
        assertThat(entity.getDeclaredField(relationship).getAnnotation(MapsId.class).value()).isEqualTo(idComponent);
        Class<?> idType = entity.getDeclaredField("id").getType();
        assertThat(idType.getDeclaredField(idComponent).getType()).isEqualTo(Long.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dateMappingBindsAndExtractsTimeWithoutTruncatingToMidnight() throws Exception {
        var value = LocalDateTime.of(2026, 11, 12, 18, 47, 35);
        var type = (BasicType<LocalDateTime>) metadata.getEntityBinding(Usuario.class.getName())
                .getProperty("dtCadastro").getType();
        var statement = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        var options = mock(WrapperOptions.class);
        type.getJdbcValueBinder().bind(statement, value, 1, options);
        verify(statement).setTimestamp(1, Timestamp.valueOf(value));
        when(result.getTimestamp(1)).thenReturn(Timestamp.valueOf(value));
        assertThat(type.getJdbcValueExtractor().extract(result, 1, options)).isEqualTo(value);
    }

    @Test
    @SuppressWarnings("unchecked")
    void numberTenIdBindsAndExtractsValuesBeyondIntegerRange() throws Exception {
        long value = 9_999_999_999L;
        var type = (BasicType<Long>) metadata.getEntityBinding(Ocorrencia.class.getName()).getIdentifier().getType();
        var statement = mock(PreparedStatement.class);
        var result = mock(ResultSet.class);
        var options = mock(WrapperOptions.class);
        type.getJdbcValueBinder().bind(statement, value, 1, options);
        verify(statement).setBigDecimal(1, BigDecimal.valueOf(value));
        when(result.getBigDecimal(1)).thenReturn(BigDecimal.valueOf(value));
        assertThat(type.getJdbcValueExtractor().extract(result, 1, options)).isEqualTo(value);
    }

    private static final class RejectingConnectionProvider implements ConnectionProvider {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public Connection getConnection() {
            attempts.incrementAndGet();
            throw new AssertionError("Acesso JDBC proibido nos testes de mapeamento");
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
