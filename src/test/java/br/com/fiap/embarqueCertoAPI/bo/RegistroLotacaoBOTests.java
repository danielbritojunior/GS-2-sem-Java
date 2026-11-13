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

class RegistroLotacaoBOTests extends BoTestSupport {

    private final RegistroLotacaoDAO registroLotacaoDAO = mock(RegistroLotacaoDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final LinhaDAO linhaDAO = mock(LinhaDAO.class);
    private final EstacaoDAO estacaoDAO = mock(EstacaoDAO.class);
    private final LinhaEstacaoDAO linhaEstacaoDAO = mock(LinhaEstacaoDAO.class);
    private final Usuario referenciaUsuario = usuario();
    private final Linha referenciaLinha = linha();
    private final Estacao referenciaEstacao = estacao();
    private RegistroLotacaoBO bo;

    @BeforeEach
    void prepare() {
        bo = new RegistroLotacaoBO(registroLotacaoDAO, usuarioDAO, linhaDAO, estacaoDAO, linhaEstacaoDAO, validator, CLOCK);
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaUsuario));
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaLinha));
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaEstacao));
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(true);
    }

    @Test
    void readsExistingMissingAndEmptyList() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.buscarPorId(1L)).isSameAs(existing);
        assertThatThrownBy(() -> bo.buscarPorId(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        when(registroLotacaoDAO.listar()).thenReturn(List.of(existing));
        assertThat(bo.listar()).containsExactly(existing);
        when(registroLotacaoDAO.listar()).thenReturn(List.of());
        assertThat(bo.listar()).isEmpty();
        verify(registroLotacaoDAO, never()).inserir(any());
        verify(registroLotacaoDAO, never()).atualizar(any());
        verify(registroLotacaoDAO, never()).excluir(any());
    }

    @Test
    void rejectsNullInputAndNullIdsBeforeDaoAccess() {
        assertThatThrownBy(() -> bo.cadastrar(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(1L, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(null, registroLotacao())).isInstanceOf(RegraNegocioException.class);
        var noId = registroLotacao();
        noId.setIdRegistro(null);
        assertThatThrownBy(() -> bo.cadastrar(noId)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(registroLotacaoDAO);
    }

    @Test
    void rejectsIdsBeyondPhysicalPrecision() {
        long overflow = 10_000_000_000L;
        var value = registroLotacao();
        value.setIdRegistro(overflow);
        assertThatThrownBy(() -> bo.cadastrar(value)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(overflow)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(-overflow)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(registroLotacaoDAO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void acceptsZeroAndNegativeIdsPermittedByDdl(long id) {
        var input = registroLotacao();
        input.setIdRegistro(id);
        assertThat(bo.cadastrar(input).getIdRegistro()).isEqualTo(id);
        verify(registroLotacaoDAO).inserir(any(RegistroLotacao.class));
    }

    @Test
    void createsANewEntityAndDoesNotModifyTheInput() {
        var input = registroLotacao();
        var created = bo.cadastrar(input);
        assertThat(created).isNotSameAs(input);
        assertThat(created.getIdRegistro()).isEqualTo(input.getIdRegistro());
        assertThat(created.getDsSentido()).isEqualTo(input.getDsSentido());
        assertThat(created.getUsuario()).isSameAs(referenciaUsuario);
        assertThat(input.getUsuario()).isNotSameAs(referenciaUsuario);
        assertThat(created.getLinha()).isSameAs(referenciaLinha);
        assertThat(input.getLinha()).isNotSameAs(referenciaLinha);
        assertThat(created.getEstacao()).isSameAs(referenciaEstacao);
        assertThat(input.getEstacao()).isNotSameAs(referenciaEstacao);
        assertThat(created.getDtRegistro()).isEqualTo(NOW);
        assertThat(input.getDtRegistro()).isNull();
        verify(registroLotacaoDAO).inserir(created);
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void rejectsDuplicatePrimaryKeyWithoutOverwriting() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = registroLotacao();
        input.setDsSentido("Outro valor");
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(ConflitoException.class);
        assertThat(existing.getDsSentido()).isNotEqualTo("Outro valor");
        verify(registroLotacaoDAO, never()).inserir(any());
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void updatesTheLoadedInstanceAndAllowsOmittedBodyId() {
        var existing = registroLotacao();
        existing.setDtRegistro(BEFORE);
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = registroLotacao();
        input.setIdRegistro(null);
        input.setDsSentido("Novo valor");
        var updated = bo.atualizar(1L, input);
        assertThat(updated).isSameAs(existing);
        assertThat(updated.getIdRegistro()).isEqualTo(1L);
        assertThat(updated.getDsSentido()).isEqualTo("Novo valor");
        assertThat(input.getIdRegistro()).isNull();
        assertThat(updated.getDtRegistro()).isEqualTo(BEFORE);
        verify(registroLotacaoDAO).atualizar(existing);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsMismatchedBodyIdAndNeverUpsertsMissingRecord() {
        var input = registroLotacao();
        input.setIdRegistro(2L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(registroLotacaoDAO);
        input.setIdRegistro(1L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(registroLotacaoDAO, never()).inserir(any());
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void invalidCandidateDoesNotDirtyManagedState() {
        var existing = registroLotacao();
        String original = existing.getDsSentido();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = registroLotacao();
        input.setDsSentido("");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsSentido()).isEqualTo(original);
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void deletesOnlyExistingRecord() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        bo.excluir(1L);
        verify(registroLotacaoDAO).excluir(existing);
        assertThatThrownBy(() -> bo.excluir(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(registroLotacaoDAO, times(1)).excluir(any());
    }

    @Test
    void preservesPersistenceFailuresAndOriginalCause() {
        var cause = new SQLException("falha simulada", "23000", 1);
        var failure = new PersistenceException("falha simulada", cause);
        doThrow(failure).when(registroLotacaoDAO).inserir(any());
        assertThatThrownBy(() -> bo.cadastrar(registroLotacao())).isSameAs(failure).hasCause(cause);
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        doThrow(failure).when(registroLotacaoDAO).atualizar(any());
        assertThatThrownBy(() -> bo.atualizar(1L, registroLotacao())).isSameAs(failure).hasCause(cause);
        doThrow(failure).when(registroLotacaoDAO).excluir(any());
        assertThatThrownBy(() -> bo.excluir(1L)).isSameAs(failure).hasCause(cause);
    }

    @Test
    void acceptsExplicitHistoricalDatesAndNormalizesToSeconds() {
        var input = registroLotacao();
        input.setDtRegistro(BEFORE.withNano(123456789));
        assertThat(bo.cadastrar(input).getDtRegistro()).isEqualTo(BEFORE);
        assertThat(input.getDtRegistro().getNano()).isEqualTo(123456789);
        var existing = registroLotacao();
        existing.setDtRegistro(NOW);
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.atualizar(1L, input).getDtRegistro()).isEqualTo(BEFORE);
    }

    @Test
    void rejectsMissingUsuarioReferenceAndKeepsManagedEntityUnchanged() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = registroLotacao();
        input.setDsSentido("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsSentido()).isNotEqualTo("Nao deve ser aplicado");
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void validatesUsuarioIdBeforeLookingUpReference() {
        var input = registroLotacao();
        input.getUsuario().setIdUsuario(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getUsuario().setIdUsuario(100_000_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(usuarioDAO);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsNullUsuarioRelationship() {
        var input = registroLotacao();
        input.setUsuario(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsMissingLinhaReferenceAndKeepsManagedEntityUnchanged() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = registroLotacao();
        input.setDsSentido("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsSentido()).isNotEqualTo("Nao deve ser aplicado");
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void validatesLinhaIdBeforeLookingUpReference() {
        var input = registroLotacao();
        input.getLinha().setIdLinha(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getLinha().setIdLinha(1_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(linhaDAO);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsNullLinhaRelationship() {
        var input = registroLotacao();
        input.setLinha(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsMissingEstacaoReferenceAndKeepsManagedEntityUnchanged() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = registroLotacao();
        input.setDsSentido("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsSentido()).isNotEqualTo("Nao deve ser aplicado");
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void validatesEstacaoIdBeforeLookingUpReference() {
        var input = registroLotacao();
        input.getEstacao().setIdEstacao(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getEstacao().setIdEstacao(100_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(estacaoDAO);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsNullEstacaoRelationship() {
        var input = registroLotacao();
        input.setEstacao(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void rejectsStationOutsideLineWithoutDirtyingManagedEntity() {
        var existing = registroLotacao();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(false);
        var input = registroLotacao();
        input.setDsSentido("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("nao pertence");
        assertThat(existing.getDsSentido()).isNotEqualTo("Nao deve ser aplicado");
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsOutOfRangeCrowdingLevel(int level) {
        var input = registroLotacao();
        input.setNrNivel(level);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(registroLotacaoDAO, never()).inserir(any());
    }
}
