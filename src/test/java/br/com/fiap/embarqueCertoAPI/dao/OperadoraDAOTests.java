package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Operadora;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperadoraDAOTests {

    @Test
    void findsExistingAndMissingReferencesWithoutWriting() {
        var em = mock(EntityManager.class);
        var dao = new OperadoraDAO();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var entity = new Operadora();
        when(em.find(Operadora.class, 1L)).thenReturn(entity);
        assertThat(dao.buscarPorId(1L)).containsSame(entity);
        assertThat(dao.buscarPorId(2L)).isEmpty();
        verify(em).find(Operadora.class, 1L);
        verify(em).find(Operadora.class, 2L);
        verifyNoMoreInteractions(em);
    }

    @Test
    void preservesLookupFailure() {
        var em = mock(EntityManager.class);
        var dao = new OperadoraDAO();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var failure = new PersistenceException("falha simulada");
        when(em.find(Operadora.class, 1L)).thenThrow(failure);
        assertThatThrownBy(() -> dao.buscarPorId(1L)).isSameAs(failure);
    }
}
