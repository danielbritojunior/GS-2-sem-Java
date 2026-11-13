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

class OcorrenciaBOTests extends BoTestSupport {

    private final OcorrenciaDAO ocorrenciaDAO = mock(OcorrenciaDAO.class);
    private final TipoOcorrenciaDAO tipoOcorrenciaDAO = mock(TipoOcorrenciaDAO.class);
    private final LinhaDAO linhaDAO = mock(LinhaDAO.class);
    private final EstacaoDAO estacaoDAO = mock(EstacaoDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final LinhaEstacaoDAO linhaEstacaoDAO = mock(LinhaEstacaoDAO.class);
    private final TipoOcorrencia referenciaTipoOcorrencia = tipoOcorrencia();
    private final Linha referenciaLinha = linha();
    private final Estacao referenciaEstacao = estacao();
    private final Usuario referenciaUsuario = usuario();
    private OcorrenciaBO bo;

    @BeforeEach
    void prepare() {
        bo = new OcorrenciaBO(ocorrenciaDAO, tipoOcorrenciaDAO, linhaDAO, estacaoDAO, usuarioDAO, linhaEstacaoDAO, validator, CLOCK);
        when(tipoOcorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaTipoOcorrencia));
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaLinha));
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaEstacao));
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.of(referenciaUsuario));
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(true);
    }

    @Test
    void readsExistingMissingAndEmptyList() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.buscarPorId(1L)).isSameAs(existing);
        assertThatThrownBy(() -> bo.buscarPorId(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        when(ocorrenciaDAO.listar()).thenReturn(List.of(existing));
        assertThat(bo.listar()).containsExactly(existing);
        when(ocorrenciaDAO.listar()).thenReturn(List.of());
        assertThat(bo.listar()).isEmpty();
        verify(ocorrenciaDAO, never()).inserir(any());
        verify(ocorrenciaDAO, never()).atualizar(any());
        verify(ocorrenciaDAO, never()).excluir(any());
    }

    @Test
    void rejectsNullInputAndNullIdsBeforeDaoAccess() {
        assertThatThrownBy(() -> bo.cadastrar(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(1L, null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.atualizar(null, ocorrencia())).isInstanceOf(RegraNegocioException.class);
        var noId = ocorrencia();
        noId.setIdOcorrencia(null);
        assertThatThrownBy(() -> bo.cadastrar(noId)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(ocorrenciaDAO);
    }

    @Test
    void rejectsIdsBeyondPhysicalPrecision() {
        long overflow = 10_000_000_000L;
        var value = ocorrencia();
        value.setIdOcorrencia(overflow);
        assertThatThrownBy(() -> bo.cadastrar(value)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.buscarPorId(overflow)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> bo.excluir(-overflow)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(ocorrenciaDAO);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void acceptsZeroAndNegativeIdsPermittedByDdl(long id) {
        var input = ocorrencia();
        input.setIdOcorrencia(id);
        assertThat(bo.cadastrar(input).getIdOcorrencia()).isEqualTo(id);
        verify(ocorrenciaDAO).inserir(any(Ocorrencia.class));
    }

    @Test
    void createsANewEntityAndDoesNotModifyTheInput() {
        var input = ocorrencia();
        var created = bo.cadastrar(input);
        assertThat(created).isNotSameAs(input);
        assertThat(created.getIdOcorrencia()).isEqualTo(input.getIdOcorrencia());
        assertThat(created.getDsOcorrencia()).isEqualTo(input.getDsOcorrencia());
        assertThat(created.getTipoOcorrencia()).isSameAs(referenciaTipoOcorrencia);
        assertThat(input.getTipoOcorrencia()).isNotSameAs(referenciaTipoOcorrencia);
        assertThat(created.getLinha()).isSameAs(referenciaLinha);
        assertThat(input.getLinha()).isNotSameAs(referenciaLinha);
        assertThat(created.getEstacao()).isSameAs(referenciaEstacao);
        assertThat(input.getEstacao()).isNotSameAs(referenciaEstacao);
        assertThat(created.getUsuario()).isSameAs(referenciaUsuario);
        assertThat(input.getUsuario()).isNotSameAs(referenciaUsuario);
        assertThat(created.getDtInicio()).isEqualTo(NOW);
        assertThat(input.getDtInicio()).isNull();
        verify(ocorrenciaDAO).inserir(created);
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void rejectsDuplicatePrimaryKeyWithoutOverwriting() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = ocorrencia();
        input.setDsOcorrencia("Outro valor");
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(ConflitoException.class);
        assertThat(existing.getDsOcorrencia()).isNotEqualTo("Outro valor");
        verify(ocorrenciaDAO, never()).inserir(any());
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void updatesTheLoadedInstanceAndAllowsOmittedBodyId() {
        var existing = ocorrencia();
        existing.setDtInicio(BEFORE);
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = ocorrencia();
        input.setIdOcorrencia(null);
        input.setDsOcorrencia("Novo valor");
        var updated = bo.atualizar(1L, input);
        assertThat(updated).isSameAs(existing);
        assertThat(updated.getIdOcorrencia()).isEqualTo(1L);
        assertThat(updated.getDsOcorrencia()).isEqualTo("Novo valor");
        assertThat(input.getIdOcorrencia()).isNull();
        assertThat(updated.getDtInicio()).isEqualTo(BEFORE);
        verify(ocorrenciaDAO).atualizar(existing);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsMismatchedBodyIdAndNeverUpsertsMissingRecord() {
        var input = ocorrencia();
        input.setIdOcorrencia(2L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(ocorrenciaDAO);
        input.setIdOcorrencia(1L);
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(ocorrenciaDAO, never()).inserir(any());
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void invalidCandidateDoesNotDirtyManagedState() {
        var existing = ocorrencia();
        String original = existing.getDsOcorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        var input = ocorrencia();
        input.setDsOcorrencia("");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsOcorrencia()).isEqualTo(original);
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void deletesOnlyExistingRecord() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        bo.excluir(1L);
        verify(ocorrenciaDAO).excluir(existing);
        assertThatThrownBy(() -> bo.excluir(2L)).isInstanceOf(RegistroNaoEncontradoException.class);
        verify(ocorrenciaDAO, times(1)).excluir(any());
    }

    @Test
    void blocksDeletionWithDependentRecords() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(ocorrenciaDAO.possuiDependencias(1L)).thenReturn(true);
        assertThatThrownBy(() -> bo.excluir(1L)).isInstanceOf(ConflitoException.class);
        verify(ocorrenciaDAO, never()).excluir(any());
    }

    @Test
    void preservesPersistenceFailuresAndOriginalCause() {
        var cause = new SQLException("falha simulada", "23000", 1);
        var failure = new PersistenceException("falha simulada", cause);
        doThrow(failure).when(ocorrenciaDAO).inserir(any());
        assertThatThrownBy(() -> bo.cadastrar(ocorrencia())).isSameAs(failure).hasCause(cause);
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        doThrow(failure).when(ocorrenciaDAO).atualizar(any());
        assertThatThrownBy(() -> bo.atualizar(1L, ocorrencia())).isSameAs(failure).hasCause(cause);
        doThrow(failure).when(ocorrenciaDAO).excluir(any());
        assertThatThrownBy(() -> bo.excluir(1L)).isSameAs(failure).hasCause(cause);
    }

    @Test
    void acceptsExplicitHistoricalDatesAndNormalizesToSeconds() {
        var input = ocorrencia();
        input.setDtInicio(BEFORE.withNano(123456789));
        assertThat(bo.cadastrar(input).getDtInicio()).isEqualTo(BEFORE);
        assertThat(input.getDtInicio().getNano()).isEqualTo(123456789);
        var existing = ocorrencia();
        existing.setDtInicio(NOW);
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        assertThat(bo.atualizar(1L, input).getDtInicio()).isEqualTo(BEFORE);
    }

    @Test
    void rejectsMissingTipoOcorrenciaReferenceAndKeepsManagedEntityUnchanged() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(tipoOcorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = ocorrencia();
        input.setDsOcorrencia("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsOcorrencia()).isNotEqualTo("Nao deve ser aplicado");
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void validatesTipoOcorrenciaIdBeforeLookingUpReference() {
        var input = ocorrencia();
        input.getTipoOcorrencia().setIdTipoOcorrencia(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getTipoOcorrencia().setIdTipoOcorrencia(1_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(tipoOcorrenciaDAO);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsNullTipoOcorrenciaRelationship() {
        var input = ocorrencia();
        input.setTipoOcorrencia(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsMissingLinhaReferenceAndKeepsManagedEntityUnchanged() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = ocorrencia();
        input.setDsOcorrencia("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsOcorrencia()).isNotEqualTo("Nao deve ser aplicado");
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void validatesLinhaIdBeforeLookingUpReference() {
        var input = ocorrencia();
        input.getLinha().setIdLinha(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getLinha().setIdLinha(1_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(linhaDAO);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsNullLinhaRelationship() {
        var input = ocorrencia();
        input.setLinha(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsMissingEstacaoReferenceAndKeepsManagedEntityUnchanged() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = ocorrencia();
        input.setDsOcorrencia("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsOcorrencia()).isNotEqualTo("Nao deve ser aplicado");
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void validatesEstacaoIdBeforeLookingUpReference() {
        var input = ocorrencia();
        input.getEstacao().setIdEstacao(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getEstacao().setIdEstacao(100_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(estacaoDAO);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsMissingUsuarioReferenceAndKeepsManagedEntityUnchanged() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        var input = ocorrencia();
        input.setDsOcorrencia("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class);
        assertThat(existing.getDsOcorrencia()).isNotEqualTo("Nao deve ser aplicado");
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void validatesUsuarioIdBeforeLookingUpReference() {
        var input = ocorrencia();
        input.getUsuario().setIdUsuario(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.getUsuario().setIdUsuario(100_000_000L);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(usuarioDAO);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @Test
    void rejectsStationOutsideLineWithoutDirtyingManagedEntity() {
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(false);
        var input = ocorrencia();
        input.setDsOcorrencia("Nao deve ser aplicado");
        assertThatThrownBy(() -> bo.atualizar(1L, input)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("nao pertence");
        assertThat(existing.getDsOcorrencia()).isNotEqualTo("Nao deve ser aplicado");
        verify(ocorrenciaDAO, never()).atualizar(any());
    }

    @Test
    void allowsLineWideOfficialNoticeAndClearsOptionalRelationshipsOnUpdate() {
        var input = ocorrencia();
        input.setTpFonte("OPERADORA");
        input.setUsuario(null);
        input.setEstacao(null);
        var created = bo.cadastrar(input);
        assertThat(created.getUsuario()).isNull();
        assertThat(created.getEstacao()).isNull();
        verifyNoInteractions(usuarioDAO, estacaoDAO, linhaEstacaoDAO);
        var existing = ocorrencia();
        when(ocorrenciaDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        bo.atualizar(1L, input);
        assertThat(existing.getUsuario()).isNull();
        assertThat(existing.getEstacao()).isNull();
    }

    @Test
    void validatesSourceAuthorAndEndAgainstStatus() {
        var input = ocorrencia();
        input.setUsuario(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.setUsuario(usuario());
        input.setTpFonte("OPERADORA");
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.setTpFonte("USUARIO");
        input.setDtFim(NOW);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.setStOcorrencia("ENCERRADA");
        input.setDtInicio(NOW);
        input.setDtFim(BEFORE);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        input.setDtFim(null);
        assertThatThrownBy(() -> bo.cadastrar(input)).isInstanceOf(RegraNegocioException.class);
        verify(ocorrenciaDAO, never()).inserir(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENCERRADA", "DESCARTADA"})
    void allowsConsistentTerminalStatusWithoutAutomatingConfirmation(String status) {
        var input = ocorrencia();
        input.setStOcorrencia(status);
        input.setDtInicio(BEFORE);
        input.setDtFim(NOW.withNano(123));
        var created = bo.cadastrar(input);
        assertThat(created.getStOcorrencia()).isEqualTo(status);
        assertThat(created.getDtFim()).isEqualTo(NOW);
    }
}
