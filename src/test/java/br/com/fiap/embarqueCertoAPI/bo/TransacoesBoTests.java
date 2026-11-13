package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.config.RelogioConfig;
import br.com.fiap.embarqueCertoAPI.dao.*;
import br.com.fiap.embarqueCertoAPI.exception.ConflitoException;
import br.com.fiap.embarqueCertoAPI.exception.RegraNegocioException;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import br.com.fiap.embarqueCertoAPI.model.*;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifica proxies reais do Spring com transacoes simuladas e nenhum DataSource. */
class TransacoesBoTests extends BoTestSupport {

    private final EstacaoDAO estacaoDAO = mock(EstacaoDAO.class);
    private final LinhaDAO linhaDAO = mock(LinhaDAO.class);
    private final OcorrenciaDAO ocorrenciaDAO = mock(OcorrenciaDAO.class);
    private final RegistroLotacaoDAO registroLotacaoDAO = mock(RegistroLotacaoDAO.class);
    private final OperadoraDAO operadoraDAO = mock(OperadoraDAO.class);
    private final TipoOcorrenciaDAO tipoOcorrenciaDAO = mock(TipoOcorrenciaDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final LinhaEstacaoDAO linhaEstacaoDAO = mock(LinhaEstacaoDAO.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private AnnotationConfigApplicationContext context;

    record Caso(String nome, Class<?> tipo) {
        @Override
        public String toString() {
            return nome;
        }
    }

    static Stream<Caso> casos() {
        return Stream.of(new Caso("Estacao", EstacaoBO.class), new Caso("Linha", LinhaBO.class),
                new Caso("Ocorrencia", OcorrenciaBO.class), new Caso("RegistroLotacao", RegistroLotacaoBO.class));
    }

    static Stream<Caso> comDependencias() {
        return casos().filter(c -> !c.nome().equals("RegistroLotacao"));
    }

    static Stream<Caso> comDto() {
        return casos().filter(c -> !c.nome().equals("Estacao"));
    }

    @BeforeEach
    void createContextWithMocksOnly() {
        when(operadoraDAO.buscarPorId(1L)).thenReturn(Optional.of(operadora()));
        when(tipoOcorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(tipoOcorrencia()));
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.of(usuario()));
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(linha()));
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(estacao()));
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(true);
        context = new AnnotationConfigApplicationContext();
        context.registerBean("transactionManager", PlatformTransactionManager.class, () -> transactions);
        context.registerBean(EstacaoBO.class, () -> new EstacaoBO(estacaoDAO, validator));
        context.registerBean(LinhaBO.class, () -> new LinhaBO(linhaDAO, operadoraDAO, validator));
        context.registerBean(OcorrenciaBO.class, () -> new OcorrenciaBO(ocorrenciaDAO, tipoOcorrenciaDAO,
                linhaDAO, estacaoDAO, usuarioDAO, linhaEstacaoDAO, validator, CLOCK));
        context.registerBean(RegistroLotacaoBO.class, () -> new RegistroLotacaoBO(registroLotacaoDAO,
                usuarioDAO, linhaDAO, estacaoDAO, linhaEstacaoDAO, validator, CLOCK));
        context.register(TxConfig.class);
        context.refresh();
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @ParameterizedTest
    @MethodSource("casos")
    void readsUseReadOnlyTransactions(Caso caso) {
        assertThat(AopUtils.isAopProxy(context.getBean(caso.tipo()))).isTrue();
        existente(caso, dados(caso));
        call(caso, "buscarPorId", 1L);
        call(caso, "listar");
        assertThat(transactions.events).containsExactly("begin:read", "commit", "begin:read", "commit");
    }

    @ParameterizedTest
    @MethodSource("casos")
    void eachMutationUsesOneWriteTransaction(Caso caso) {
        existente(caso, null);
        call(caso, "cadastrar", dados(caso));
        existente(caso, dados(caso));
        call(caso, "atualizar", 1L, dados(caso));
        call(caso, "excluir", 1L);
        assertThat(transactions.events).containsExactly("begin:write", "commit",
                "begin:write", "commit", "begin:write", "commit");
    }

    @ParameterizedTest
    @MethodSource("casos")
    void validationFailureRollsBack(Caso caso) {
        assertThatThrownBy(() -> call(caso, "cadastrar", (Object) null))
                .isInstanceOf(RegraNegocioException.class);
        assertThat(transactions.events).containsExactly("begin:write", "rollback");
    }

    @ParameterizedTest
    @MethodSource("casos")
    void duplicateIdRollsBack(Caso caso) {
        existente(caso, dados(caso));
        assertThatThrownBy(() -> call(caso, "cadastrar", dados(caso))).isInstanceOf(ConflitoException.class);
        assertThat(transactions.events).containsExactly("begin:write", "rollback");
    }

    @ParameterizedTest
    @MethodSource("comDependencias")
    void blockedDeletionRollsBack(Caso caso) {
        existente(caso, dados(caso));
        when(ReflectionTestUtils.<Boolean>invokeMethod(dao(caso), "possuiDependencias", 1L)).thenReturn(true);
        assertThatThrownBy(() -> call(caso, "excluir", 1L)).isInstanceOf(ConflitoException.class);
        assertThat(transactions.events).containsExactly("begin:write", "rollback");
    }

    @ParameterizedTest
    @MethodSource("casos")
    void persistenceFailureRollsBackAndKeepsOriginalCause(Caso caso) {
        existente(caso, null);
        var cause = new SQLException("falha simulada", "23000", 1);
        var failure = new PersistenceException("falha simulada", cause);
        switch (caso.nome()) {
            case "Estacao" -> doThrow(failure).when(estacaoDAO).inserir(any());
            case "Linha" -> doThrow(failure).when(linhaDAO).inserir(any());
            case "Ocorrencia" -> doThrow(failure).when(ocorrenciaDAO).inserir(any());
            case "RegistroLotacao" -> doThrow(failure).when(registroLotacaoDAO).inserir(any());
            default -> throw new AssertionError(caso);
        }
        assertThatThrownBy(() -> call(caso, "cadastrar", dados(caso))).isSameAs(failure).hasCause(cause);
        assertThat(transactions.events).containsExactly("begin:write", "rollback");
    }

    @ParameterizedTest
    @MethodSource("casos")
    void commitFailureReachesCallerWithOriginalCause(Caso caso) {
        existente(caso, null);
        var cause = new SQLException("duplicidade concorrente simulada", "23000", 1);
        var failure = new DataIntegrityViolationException("commit simulado", cause);
        transactions.commitFailure = failure;
        assertThatThrownBy(() -> call(caso, "cadastrar", dados(caso))).isSameAs(failure).hasCause(cause);
        assertThat(transactions.events).containsExactly("begin:write", "commit", "rollback");
    }

    @ParameterizedTest
    @MethodSource("comDto")
    void dtoMethodsKeepTheOriginalReadAndWriteTransactions(Caso caso) {
        existente(caso, dados(caso));
        Object resposta = call(caso, "buscarRespostaPorId", 1L);
        assertThat(resposta.getClass().getSimpleName()).isEqualTo(caso.nome() + "Response");
        when(ReflectionTestUtils.<List<Object>>invokeMethod(dao(caso), "listar"))
                .thenReturn(List.of(dados(caso)));
        var lista = (List<?>) call(caso, "listarRespostas");
        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().getClass()).isEqualTo(resposta.getClass());
        existente(caso, null);
        assertThat(call(caso, "cadastrarResposta", dados(caso)).getClass()).isEqualTo(resposta.getClass());
        existente(caso, dados(caso));
        assertThat(call(caso, "atualizarResposta", 1L, dados(caso)).getClass()).isEqualTo(resposta.getClass());
        assertThat(transactions.events).containsExactly("begin:read", "commit", "begin:read", "commit",
                "begin:write", "commit", "begin:write", "commit");
    }

    @ParameterizedTest
    @MethodSource("comDto")
    void lazyIdentifiersAreReadInsideTransactionAndDtoSerializesAfterIt(Caso caso) throws Exception {
        Object entity = dados(caso);
        switch (caso.nome()) {
            case "Linha" -> ((Linha) entity).setOperadora(referencia(Operadora.class, "getIdOperadora"));
            case "Ocorrencia" -> {
                var ocorrencia = (Ocorrencia) entity;
                ocorrencia.setTipoOcorrencia(referencia(TipoOcorrencia.class, "getIdTipoOcorrencia"));
                ocorrencia.setLinha(referencia(Linha.class, "getIdLinha"));
                ocorrencia.setEstacao(referencia(Estacao.class, "getIdEstacao"));
                ocorrencia.setUsuario(referencia(Usuario.class, "getIdUsuario"));
            }
            case "RegistroLotacao" -> {
                var registro = (RegistroLotacao) entity;
                registro.setUsuario(referencia(Usuario.class, "getIdUsuario"));
                registro.setLinha(referencia(Linha.class, "getIdLinha"));
                registro.setEstacao(referencia(Estacao.class, "getIdEstacao"));
            }
            default -> throw new AssertionError(caso);
        }
        existente(caso, entity);
        when(ReflectionTestUtils.<List<Object>>invokeMethod(dao(caso), "listar")).thenReturn(List.of(entity));
        Object resposta = call(caso, "buscarRespostaPorId", 1L);
        Object lista = call(caso, "listarRespostas");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(resposta)).contains("id").doesNotContain("hibernateLazyInitializer");
        assertThat(mapper.writeValueAsString(lista)).startsWith("[");
    }

    private <T> T referencia(Class<T> tipo, String getterId) {
        return mock(tipo, invocation -> {
            if (invocation.getMethod().getName().equals(getterId)) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                        .as("O identificador LAZY deve ser convertido dentro da BO transacional").isTrue();
                return 1L;
            }
            return RETURNS_DEFAULTS.answer(invocation);
        });
    }

    @Test
    void clockUsesTheApplicationTimeZone() {
        assertThat(new RelogioConfig().clock().getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }

    private Object dados(Caso caso) {
        return switch (caso.nome()) {
            case "Estacao" -> estacao();
            case "Linha" -> linha();
            case "Ocorrencia" -> ocorrencia();
            case "RegistroLotacao" -> registroLotacao();
            default -> throw new AssertionError(caso);
        };
    }

    private Object dao(Caso caso) {
        return switch (caso.nome()) {
            case "Estacao" -> estacaoDAO;
            case "Linha" -> linhaDAO;
            case "Ocorrencia" -> ocorrenciaDAO;
            case "RegistroLotacao" -> registroLotacaoDAO;
            default -> throw new AssertionError(caso);
        };
    }

    private void existente(Caso caso, Object valor) {
        when(ReflectionTestUtils.<Optional<Object>>invokeMethod(dao(caso), "buscarPorId", 1L))
                .thenReturn(Optional.ofNullable(valor));
    }

    private Object call(Caso caso, String method, Object... arguments) {
        return ReflectionTestUtils.invokeMethod(context.getBean(caso.tipo()), method, arguments);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TxConfig {
    }

    private static final class RecordingTransactions extends AbstractPlatformTransactionManager {
        final List<String> events = new ArrayList<>();
        RuntimeException commitFailure;

        RecordingTransactions() {
            setRollbackOnCommitFailure(true);
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            events.add(definition.isReadOnly() ? "begin:read" : "begin:write");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
            if (commitFailure != null) {
                throw commitFailure;
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }
    }
}
