package br.com.fiap.embarqueCertoAPI.controller;

import br.com.fiap.embarqueCertoAPI.bo.RegistroLotacaoBO;
import br.com.fiap.embarqueCertoAPI.dao.*;
import br.com.fiap.embarqueCertoAPI.model.*;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Controller e BO reais; somente DAOs simuladas, sem EntityManager ou DataSource. */
@WebMvcTest(RegistroLotacaoController.class)
@Import(RegistroLotacaoBO.class)
class RegistroLotacaoControllerTests {

    private static final String BASE = "/api/registros-lotacao";
    private static final String JSON_VALIDO = "{\"idRegistro\":1,\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":3,\"dsSentido\":\"Centro\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private RegistroLotacaoDAO registroLotacaoDAO;

    @MockitoBean
    private UsuarioDAO usuarioDAO;

    @MockitoBean
    private LinhaDAO linhaDAO;

    @MockitoBean
    private EstacaoDAO estacaoDAO;

    @MockitoBean
    private LinhaEstacaoDAO linhaEstacaoDAO;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void prepare() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        var usuario = new Usuario();
        usuario.setIdUsuario(1L);
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.of(usuario));
        var linha = new Linha();
        linha.setIdLinha(1L);
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.of(linha));
        var estacao = new Estacao();
        estacao.setIdEstacao(1L);
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(estacao));
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(true);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-12T15:30:45Z"));
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
    }

    private RegistroLotacao existente() {
        var entity = new RegistroLotacao();
        entity.setIdRegistro(1L);
        var usuario = new Usuario();
        usuario.setIdUsuario(1L);
        entity.setUsuario(usuario);
        var linha = new Linha();
        linha.setIdLinha(1L);
        entity.setLinha(linha);
        var estacao = new Estacao();
        estacao.setIdEstacao(1L);
        entity.setEstacao(estacao);
        entity.setNrNivel(3);
        entity.setDsSentido("Centro");
        entity.setDtRegistro(LocalDateTime.of(2026, 11, 12, 8, 30, 0));
        return entity;
    }

    @Test
    void listsAndSerializesOnlyTheResourceFields() throws Exception {
        when(registroLotacaoDAO.listar()).thenReturn(List.of(existente()));
        mvc.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$[0].idRegistro").value(1))
                .andExpect(jsonPath("$[0].dsSentido").value("Centro"))
                .andExpect(jsonPath("$[0].idUsuario").value(1))
                .andExpect(jsonPath("$[0].usuario").doesNotExist())
                .andExpect(jsonPath("$[0].idLinha").value(1))
                .andExpect(jsonPath("$[0].linha").doesNotExist())
                .andExpect(jsonPath("$[0].idEstacao").value(1))
                .andExpect(jsonPath("$[0].estacao").doesNotExist())
                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void emptyListIsAnArrayWithStatus200() throws Exception {
        when(registroLotacaoDAO.listar()).thenReturn(List.of());
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void findsById() throws Exception {
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existente()));
        mvc.perform(get(BASE + "/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.idRegistro").value(1))
                .andExpect(jsonPath("$.dsSentido").value("Centro"));
    }

    @Test
    void createsWith201LocationAndConvertedInput() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated()).andExpect(header().string("Location", BASE + "/1"))
                .andExpect(jsonPath("$.idRegistro").value(1))
                .andExpect(jsonPath("$.dsSentido").value("Centro"))
                .andExpect(jsonPath("$.dtRegistro").value("2026-11-12T12:30:45"))
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
        var captor = ArgumentCaptor.forClass(RegistroLotacao.class);
        verify(registroLotacaoDAO).inserir(captor.capture());
        assertThat(captor.getValue().getIdRegistro()).isEqualTo(1L);
        assertThat(captor.getValue().getUsuario().getIdUsuario()).isEqualTo(1L);
        assertThat(captor.getValue().getLinha().getIdLinha()).isEqualTo(1L);
        assertThat(captor.getValue().getEstacao().getIdEstacao()).isEqualTo(1L);
    }

    @Test
    void updatesWithOmittedBodyIdAndPreservesIdentity() throws Exception {
        var entity = existente();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(entity));
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("{\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":3,\"dsSentido\":\"Centro\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idRegistro").value(1));
        verify(registroLotacaoDAO).atualizar(entity);
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void deletesWith204AndNoBody() throws Exception {
        var entity = existente();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(entity));
        mvc.perform(delete(BASE + "/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(registroLotacaoDAO).excluir(entity);
    }

    @Test
    void missingRecordsReturn404ForGetPutAndDelete() throws Exception {
        mvc.perform(get(BASE + "/2")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.caminho").value(BASE + "/2"));
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/2")).andExpect(status().isNotFound());
        verify(registroLotacaoDAO, never()).inserir(any());
        verify(registroLotacaoDAO, never()).atualizar(any());
        verify(registroLotacaoDAO, never()).excluir(any());
    }

    @Test
    void invalidPostAndPutUseModelValidationAndDoNotWrite() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"idRegistro\":1,\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":6,\"dsSentido\":\"Centro\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        var existing = existente();
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("{\"idRegistro\":1,\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":6,\"dsSentido\":\"Centro\"}"))
                .andExpect(status().isBadRequest());
        assertThat(existing.getDsSentido()).isEqualTo("Centro");
        verify(registroLotacaoDAO, never()).inserir(any());
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void createRequiresIdAndUpdateRejectsDifferentId() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":3,\"dsSentido\":\"Centro\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("{\"idRegistro\":2,\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":3,\"dsSentido\":\"Centro\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registroLotacaoDAO);
    }

    @Test
    void duplicateIdReturns409WithoutOverwrite() throws Exception {
        when(registroLotacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existente()));
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.caminho").value(BASE));
        verify(registroLotacaoDAO, never()).inserir(any());
        verify(registroLotacaoDAO, never()).atualizar(any());
    }

    @Test
    void malformedJsonAndFractionalIdsReturn400() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"idRegistro\":1.5,\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":3,\"dsSentido\":\"Centro\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(registroLotacaoDAO);
    }

    @Test
    void missingOrNullBodyReturns400() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registroLotacaoDAO);
    }

    @Test
    void integrityRaceReturnsGeneric409WithoutSqlDetails() throws Exception {
        var sql = new SQLException("ORA-00001 simulated schema and SQL detail", "23000", 1);
        var failure = new DataIntegrityViolationException("insert into simulated SQL", sql);
        doThrow(failure).when(registroLotacaoDAO).inserir(any());
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value(containsString("integridade")))
                .andExpect(content().string(not(containsString("ORA-"))))
                .andExpect(content().string(not(containsString("insert into"))));
        assertThat(failure.getCause()).isSameAs(sql);
    }

    @Test
    void unexpectedFailureReturnsGeneric500() throws Exception {
        when(registroLotacaoDAO.listar()).thenThrow(new DataAccessResourceFailureException("simulated connection detail"));
        mvc.perform(get(BASE)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.caminho").value(BASE))
                .andExpect(content().string(not(containsString("simulated connection detail"))));
    }

    @Test
    void frameworkMethodAndMediaErrorsKeepTheirStatuses() throws Exception {
        mvc.perform(patch(BASE + "/1").contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
        mvc.perform(post(BASE).contentType("text/plain").content(JSON_VALIDO))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void missingUsuarioIsA400ReferenceError() throws Exception {
        when(usuarioDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO)).andExpect(status().isBadRequest());
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void missingLinhaIsA400ReferenceError() throws Exception {
        when(linhaDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO)).andExpect(status().isBadRequest());
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void missingEstacaoIsA400ReferenceError() throws Exception {
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.empty());
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO)).andExpect(status().isBadRequest());
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void stationOutsideLineReturns400() throws Exception {
        when(linhaEstacaoDAO.existePorId(new LinhaEstacaoId(1L, 1L))).thenReturn(false);
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensagem").value(containsString("nao pertence")));
        verify(registroLotacaoDAO, never()).inserir(any());
    }

    @Test
    void invalidDateReturns400BeforeBusinessAccess() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON)
                .content("{\"idRegistro\":1,\"idUsuario\":1,\"idLinha\":1,\"idEstacao\":1,\"nrNivel\":3,\"dsSentido\":\"Centro\",\"dtRegistro\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registroLotacaoDAO);
    }
}
