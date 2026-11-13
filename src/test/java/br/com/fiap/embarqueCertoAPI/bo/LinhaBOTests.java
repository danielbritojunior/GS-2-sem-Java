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

class LinhaBOTests extends BoTestSupport {

    private final LinhaDAO linhaDAO = mock(LinhaDAO.class);
    private final OperadoraDAO operadoraDAO = mock(OperadoraDAO.class);
    private final Operadora referenciaOperadora = operadora();
    private LinhaBO bo;

    @BeforeEach
    void prepare() {
        bo = new LinhaBO(linhaDAO, operadoraDAO, validator);
        when(operadoraDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaOperadora));

    }

    @Test
    void readsExistingMissingAndEmptyList() {
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.buscarPorId(1L)).isSameAs(existing);
        assertThatThrownBy(() -> bo.buscarPorId(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        when(linhaDAO.listar()).thenReturn(List.of(existing));
        assertThat(bo.listar()).containsExactly(existing);
        when(linhaDAO.listar()).thenReturn(List.of());
        assertThat(bo.listar()).isEmpty();
        verify(linhaDAO, never()).inserir(any());
        verify(linhaDAO, never()).atualizar(any());
        verify(linhaDAO, never()).excluir(any());
    }

    @Test
    void rejectsNullInputAndNullIdsBeforeDaoAccess() {
        assertThatThrownBy(() -> bo.cadastrar(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(1L, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(null, linha())).isInstanceOf(RegraNegocioException.class);
        var noId = linha();
        noId.setIdLinha(null);
        assertThatThrownBy(() -> bo.cadastrar(noId)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(linhaDAO);
    }

    @Test
    void rejectsIdsBeyondPhysicalPrecision() {
        long overflow = 1_000L;
        var value = linha();
        value.setIdLinha(overflow);
        assertThatThrownBy(() -> bo.cadastrar(value)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(overflow)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(-overflow)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(linhaDAO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void acceptsZeroAndNegativeIdsPermittedByDdl(long id) {
        var input = linha();
        input.setIdLinha(id);
        assertThat(bo.cadastrar(input).getIdLinha()).isEqualTo(id);
        verify(linhaDAO).inserir(any(Linha.class));
    }

    @Test
    void createsANewEntityAndDoesNotModifyTheInput() {
        var input = linha();
        var created = bo.cadastrar(input);
        assertThat(created).isNotSameAs(input);
        assertThat(created.getIdLinha()).isEqualTo(input.getIdLinha());
        assertThat(created.getNmLinha()).isEqualTo(input.getNmLinha());
        assertThat(created.getOperadora()).isSameAs(referenciaOperadora);
        assertThat(input.getOperadora()).isNotSameAs(referenciaOperadora);

        verify(linhaDAO).inserir(created);
        verify(linhaDAO, never()).atualizar(any());
    }

    @Test
    void rejectsDuplicatePrimaryKeyWithoutOverwriting() {
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = linha();
        input.setNmLinha("Outro valor");
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(ConflitoException.class);
        assertThat(existing.getNmLinha()).isNotEqualTo("Outro valor");
        verify(linhaDAO, never()).inserir(any());
        verify(linhaDAO, never()).atualizar(any());
    }

    @Test
    void updatesTheLoadedInstanceAndAllowsOmittedBodyId() {
        var existing = linha();

        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = linha();
        input.setIdLinha(null);
        input.setNmLinha("Novo valor");
        var updated = bo.atualizar(1L, input);
        assertThat(updated).isSameAs(existing);
        assertThat(updated.getIdLinha()).isEqualTo(1L);
        assertThat(updated.getNmLinha()).isEqualTo("Novo valor");
        assertThat(input.getIdLinha()).isNull();

        verify(linhaDAO).atualizar(existing);
        verify(linhaDAO, never()).inserir(any());
    }

    @Test
    void rejectsMismatchedBodyIdAndNeverUpsertsMissingRecord() {
        var input = linha();
        input.setIdLinha(2L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(linhaDAO);
        input.setIdLinha(1L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(linhaDAO, never()).inserir(any());
        verify(linhaDAO, never()).atualizar(any());
    }

    @Test
    void invalidCandidateDoesNotDirtyManagedState() {
        var existing = linha();
        String original = existing.getNmLinha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = linha();
        input.setNmLinha("");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getNmLinha()).isEqualTo(original);
        verify(linhaDAO, never()).atualizar(any());
    }

    @Test
    void deletesOnlyExistingRecord() {
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        bo.excluir(1L);
        verify(linhaDAO).excluir(existing);
        assertThatThrownBy(() -> bo.excluir(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(linhaDAO, times(1)).excluir(any());
    }

    @Test
    void blocksDeletionWithDependentRecords() {
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(linhaDAO.possuiDependencias(1L)).thenReturn(true);
        assertThatThrownBy(() -> bo.excluir(1L)).isInstanceOf(ConflitoException.class);
        verify(linhaDAO, never()).excluir(any());
    }

    @Test
    void preservesPersistenceFailuresAndOriginalCause() {
        var cause = new SQLException("falha simulada", "23000", 1);
        var failure = new PersistenceException("falha simulada", cause);
        doThrow(failure).when(linhaDAO).inserir(any());
        assertThatThrownBy(() -> bo.cadastrar(linha())).isSameAs(failure).hasCause(cause);
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        doThrow(failure).when(linhaDAO).atualizar(any());
        assertThatThrownBy(() -> bo.atualizar(1L, linha())).isSameAs(failure).hasCause(cause);
        doThrow(failure).when(linhaDAO).excluir(any());
        assertThatThrownBy(() -> bo.excluir(1L)).isSameAs(failure).hasCause(cause);
    }

    @Test
    void checksUniqueValueExcludingCurrentIdAndDoesNotDirtyOnConflict() {
        var input = linha();
        when(linhaDAO.existeNumero(input.getNrLinha(), null)).thenReturn(true);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(ConflitoException.class);
        verify(linhaDAO, never()).inserir(any());

        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var changed = linha();
        changed.setNmLinha("Valor alterado");
        changed.setNrLinha(2);
        when(linhaDAO.existeNumero(changed.getNrLinha(), 1L)).thenReturn(true);
        assertThatThrownBy(() -> bo.atualizar(1L, changed)).isInstanceOf(ConflitoException.class);
        assertThat(existing.getNmLinha()).isEqualTo(input.getNmLinha());
        verify(linhaDAO, never()).atualizar(any());
    }

    @Test
    void permitsUpdateWhenUniqueValueBelongsToTheSameRecord() {
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.atualizar(1L, linha())).isSameAs(existing);
        verify(linhaDAO).existeNumero(existing.getNrLinha(), 1L);
        verify(linhaDAO).atualizar(existing);
    }

    @Test
    void rejectsMissingOperadoraReferenceAndKeepsManagedEntityUnchanged() {
        var existing = linha();
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(operadoraDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = linha();
        input.setNmLinha("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getNmLinha()).isNotEqualTo("Nao deve ser aplicado");
        verify(linhaDAO, never()).atualizar(any());
    }

    @Test
    void validatesOperadoraIdBeforeLookingUpReference() {
        var input = linha();
        input.getOperadora().setIdOperadora(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getOperadora().setIdOperadora(1_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(operadoraDAO);
        verify(linhaDAO, never()).inserir(any());
    }

    @Test
    void rejectsNullOperadoraRelationship() {
        var input = linha();
        input.setOperadora(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(linhaDAO, never()).inserir(any());
    }
}
