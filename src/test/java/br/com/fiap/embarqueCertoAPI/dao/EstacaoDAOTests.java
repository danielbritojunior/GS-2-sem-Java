package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Estacao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EstacaoDAOTests {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final EstacaoDAO dao = new EstacaoDAO();
    private final Estacao entity = new Estacao();

    @BeforeEach
    void prepare() {
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
    }

    @Test
    void findsExistingAndMissingId() {
        when(entityManager.find(Estacao.class, 7L)).thenReturn(entity);
        assertThat(dao.buscarPorId(7L)).containsSame(entity);
        assertThat(dao.buscarPorId(8L)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsUsingTypedOrderedQuery() {
        TypedQuery<Estacao> query = mock(TypedQuery.class);
        when(entityManager.createQuery(EstacaoDAO.LISTAR_JPQL, Estacao.class)).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(entity));
        assertThat(dao.listar()).containsExactly(entity);
        assertThat(EstacaoDAO.LISTAR_JPQL).isEqualTo("select e from Estacao e order by e.idEstacao");
    }

    @Test
    void insertsUsingPersistThenFlush() {
        dao.inserir(entity);
        var order = inOrder(entityManager);
        order.verify(entityManager).persist(entity);
        order.verify(entityManager).flush();
        verify(entityManager, never()).merge(any());
    }

    @Test
    void updatesOnlyManagedEntityUsingDirtyChecking() {
        when(entityManager.contains(entity)).thenReturn(true);
        dao.atualizar(entity);
        verify(entityManager).flush();
        verify(entityManager, never()).merge(any());
        verify(entityManager, never()).persist(any());
    }

    @Test
    void rejectsDetachedOrNullUpdatesAndDeletes() {
        assertThatThrownBy(() -> dao.atualizar(entity)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dao.excluir(entity)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dao.atualizar(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dao.excluir(null)).isInstanceOf(IllegalArgumentException.class);
        verify(entityManager, never()).flush();
        verify(entityManager, never()).remove(any());
    }

    @Test
    void deletesManagedEntityThenFlushes() {
        when(entityManager.contains(entity)).thenReturn(true);
        dao.excluir(entity);
        var order = inOrder(entityManager);
        order.verify(entityManager).contains(entity);
        order.verify(entityManager).remove(entity);
        order.verify(entityManager).flush();
    }

    @Test
    void preservesPersistFailureAndItsSqlCause() {
        var cause = new SQLException("falha simulada", "23000", 1);
        var failure = new PersistenceException("duplicidade simulada", cause);
        doThrow(failure).when(entityManager).persist(entity);
        assertThatThrownBy(() -> dao.inserir(entity)).isSameAs(failure).hasCause(cause);
        verify(entityManager, never()).flush();
    }

    @Test
    void preservesFlushFailureForEveryWriteOperation() {
        var cause = new SQLException("falha simulada", "23000", 2292);
        var failure = new PersistenceException("integridade simulada", cause);
        when(entityManager.contains(entity)).thenReturn(true);
        doThrow(failure).when(entityManager).flush();
        assertThatThrownBy(() -> dao.inserir(entity)).isSameAs(failure).hasCause(cause);
        assertThatThrownBy(() -> dao.atualizar(entity)).isSameAs(failure).hasCause(cause);
        assertThatThrownBy(() -> dao.excluir(entity)).isSameAs(failure).hasCause(cause);
    }

    @Test
    void preservesReadAndRemoveFailures() {
        var failure = new PersistenceException("indisponibilidade simulada");
        when(entityManager.find(Estacao.class, 7L)).thenThrow(failure);
        assertThatThrownBy(() -> dao.buscarPorId(7L)).isSameAs(failure);
        when(entityManager.contains(entity)).thenReturn(true);
        doThrow(failure).when(entityManager).remove(entity);
        assertThatThrownBy(() -> dao.excluir(entity)).isSameAs(failure);
        verify(entityManager, never()).flush();
    }

    @Test
    void checksUniquenessWithBoundValueAndIgnoredId() {
        var query = countQuery();
        when(query.getSingleResult()).thenReturn(0L, 1L);
        assertThat(dao.existeNome("Nome ' parametrizado", null)).isFalse();
        assertThat(dao.existeNome("Nome ' parametrizado", 7L)).isTrue();
        verify(entityManager, times(2)).createQuery(EstacaoDAO.UNICIDADE_JPQL, Long.class);
        verify(query, times(2)).setParameter("nome", "Nome ' parametrizado");
        verify(query).setParameter("idIgnorado", null);
        verify(query).setParameter("idIgnorado", 7L);
    }

    @Test
    void checksEveryInboundDependencyWhenNoneExists() {
        var query = countQuery();
        when(query.getSingleResult()).thenReturn(0L);
        assertThat(dao.possuiDependencias(7L)).isFalse();
        verify(entityManager).createQuery(EstacaoDAO.DEP_LINHAESTACAO_JPQL, Long.class);
        verify(entityManager).createQuery(EstacaoDAO.DEP_OCORRENCIA_JPQL, Long.class);
        verify(entityManager).createQuery(EstacaoDAO.DEP_REGISTROLOTACAO_JPQL, Long.class);
        verify(entityManager).createQuery(EstacaoDAO.DEP_TRAJETO_JPQL, Long.class);
        verify(query, times(4)).setParameter("id", 7L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void detectsEachDependencyAndStopsAtFirstMatch(int index) {
        var query = countQuery();
        var result = new java.util.concurrent.atomic.AtomicInteger();
        when(query.getSingleResult()).thenAnswer(call -> result.getAndIncrement() == index ? 1L : 0L);
        assertThat(dao.possuiDependencias(7L)).isTrue();
        verify(query, times(index + 1)).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private TypedQuery<Long> countQuery() {
        TypedQuery<Long> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), nullable(Object.class))).thenReturn(query);
        return query;
    }
}
