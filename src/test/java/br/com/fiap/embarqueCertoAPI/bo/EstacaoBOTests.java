package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.dao.*;
import br.com.fiap.embarqueCertoAPI.exception.*;
import br.com.fiap.embarqueCertoAPI.model.*;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstacaoBOTests extends BoTestSupport {

    private final EstacaoDAO estacaoDAO = mock(EstacaoDAO.class);

    private EstacaoBO bo;

    @BeforeEach
    void prepare() {
        bo = new EstacaoBO(estacaoDAO, validator);


    }

    @Test
    void readsExistingMissingAndEmptyList() {
        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.buscarPorId(1L)).isSameAs(existing);
        assertThatThrownBy(() -> bo.buscarPorId(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        when(estacaoDAO.listar()).thenReturn(List.of(existing));
        assertThat(bo.listar()).containsExactly(existing);
        when(estacaoDAO.listar()).thenReturn(List.of());
        assertThat(bo.listar()).isEmpty();
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
        verify(estacaoDAO, never()).excluir(any());
    }

    @Test
    void rejectsNullInputAndNullIdsBeforeDaoAccess() {
        assertThatThrownBy(() -> bo.cadastrar(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(1L, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(null, estacao())).isInstanceOf(RegraNegocioException.class);
        var noId = estacao();
        noId.setIdEstacao(null);
        assertThatThrownBy(() -> bo.cadastrar(noId)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(estacaoDAO);
    }

    @Test
    void rejectsIdsBeyondPhysicalPrecision() {
        long overflow = 100_000L;
        var value = estacao();
        value.setIdEstacao(overflow);
        assertThatThrownBy(() -> bo.cadastrar(value)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(overflow)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(-overflow)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(estacaoDAO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void acceptsZeroAndNegativeIdsPermittedByDdl(long id) {
        var input = estacao();
        input.setIdEstacao(id);
        assertThat(bo.cadastrar(input).getIdEstacao()).isEqualTo(id);
        verify(estacaoDAO).inserir(any(Estacao.class));
    }

    @Test
    void createsANewEntityAndDoesNotModifyTheInput() {
        var input = estacao();
        var created = bo.cadastrar(input);
        assertThat(created).isNotSameAs(input);
        assertThat(created.getIdEstacao()).isEqualTo(input.getIdEstacao());
        assertThat(created.getNmEstacao()).isEqualTo(input.getNmEstacao());


        verify(estacaoDAO).inserir(created);
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void rejectsDuplicatePrimaryKeyWithoutOverwriting() {
        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = estacao();
        input.setNmEstacao("Outro valor");
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(ConflitoException.class);
        assertThat(existing.getNmEstacao()).isNotEqualTo("Outro valor");
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void updatesTheLoadedInstanceAndAllowsOmittedBodyId() {
        var existing = estacao();

        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = estacao();
        input.setIdEstacao(null);
        input.setNmEstacao("Novo valor");
        var updated = bo.atualizar(1L, input);
        assertThat(updated).isSameAs(existing);
        assertThat(updated.getIdEstacao()).isEqualTo(1L);
        assertThat(updated.getNmEstacao()).isEqualTo("Novo valor");
        assertThat(input.getIdEstacao()).isNull();

        verify(estacaoDAO).atualizar(existing);
        verify(estacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsMismatchedBodyIdAndNeverUpsertsMissingRecord() {
        var input = estacao();
        input.setIdEstacao(2L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(estacaoDAO);
        input.setIdEstacao(1L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void invalidCandidateDoesNotDirtyManagedState() {
        var existing = estacao();
        String original = existing.getNmEstacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = estacao();
        input.setNmEstacao("");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getNmEstacao()).isEqualTo(original);
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void deletesOnlyExistingRecord() {
        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        bo.excluir(1L);
        verify(estacaoDAO).excluir(existing);
        assertThatThrownBy(() -> bo.excluir(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(estacaoDAO, times(1)).excluir(any());
    }

    @Test
    void blocksDeletionWithDependentRecords() {
        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(estacaoDAO.possuiDependencias(1L)).thenReturn(true);
        assertThatThrownBy(() -> bo.excluir(1L)).isInstanceOf(ConflitoException.class);
        verify(estacaoDAO, never()).excluir(any());
    }

    @Test
    void preservesPersistenceFailuresAndOriginalCause() {
        var cause = new SQLException("falha simulada", "23000", 1);
        var failure = new PersistenceException("falha simulada", cause);
        doThrow(failure).when(estacaoDAO).inserir(any());
        assertThatThrownBy(() -> bo.cadastrar(estacao())).isSameAs(failure).hasCause(cause);
        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        doThrow(failure).when(estacaoDAO).atualizar(any());
        assertThatThrownBy(() -> bo.atualizar(1L, estacao())).isSameAs(failure).hasCause(cause);
        doThrow(failure).when(estacaoDAO).excluir(any());
        assertThatThrownBy(() -> bo.excluir(1L)).isSameAs(failure).hasCause(cause);
    }

    @Test
    void checksUniqueValueExcludingCurrentIdAndDoesNotDirtyOnConflict() {
        var input = estacao();
        when(estacaoDAO.existeNome(input.getNmEstacao(), null)).thenReturn(true);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(ConflitoException.class);
        verify(estacaoDAO, never()).inserir(any());

        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var changed = estacao();
        changed.setNmEstacao("Valor alterado");

        when(estacaoDAO.existeNome(changed.getNmEstacao(), 1L)).thenReturn(true);
        assertThatThrownBy(() -> bo.atualizar(1L, changed)).isInstanceOf(ConflitoException.class);
        assertThat(existing.getNmEstacao()).isEqualTo(input.getNmEstacao());
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void permitsUpdateWhenUniqueValueBelongsToTheSameRecord() {
        var existing = estacao();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.atualizar(1L, estacao())).isSameAs(existing);
        verify(estacaoDAO).existeNome(existing.getNmEstacao(), 1L);
        verify(estacaoDAO).atualizar(existing);
    }
}
