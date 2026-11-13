package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.LinhaEstacao;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LinhaEstacaoDAOTests {

    @Test
    void checksTheCompleteCompositeKeyWithoutWriting() {
        var em = mock(EntityManager.class);
        var dao = new LinhaEstacaoDAO();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var id = new LinhaEstacaoId(1L, 2L);
        when(em.find(LinhaEstacao.class, id)).thenReturn(new LinhaEstacao());
        assertThat(dao.existePorId(new LinhaEstacaoId(1L, 2L))).isTrue();
        assertThat(dao.existePorId(new LinhaEstacaoId(2L, 1L))).isFalse();
        verify(em).find(LinhaEstacao.class, id);
        verify(em).find(LinhaEstacao.class, new LinhaEstacaoId(2L, 1L));
        verifyNoMoreInteractions(em);
    }

    @Test
    void preservesLookupFailure() {
        var em = mock(EntityManager.class);
        var dao = new LinhaEstacaoDAO();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var id = new LinhaEstacaoId(1L, 2L);
        var failure = new PersistenceException("falha simulada");
        when(em.find(LinhaEstacao.class, id)).thenThrow(failure);
        assertThatThrownBy(() -> dao.existePorId(id)).isSameAs(failure);
    }
}
