package br.com.fiap.embarqueCertoAPI.controller;

import br.com.fiap.embarqueCertoAPI.bo.EstacaoBO;
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
@WebMvcTest(EstacaoController.class)
@Import(EstacaoBO.class)
class EstacaoControllerTests {

    private static final String BASE = "/api/estacoes";
    private static final String JSON_VALIDO = "{\"idEstacao\":1,\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":0,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private EstacaoDAO estacaoDAO;


    @BeforeEach
    void prepare() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();


    }

    private Estacao existente() {
        var entity = new Estacao();
        entity.setIdEstacao(1L);
        entity.setNmEstacao("Luz");
        entity.setStAcessivel("S");
        entity.setNrMinBaldeacao(0);
        entity.setNrLatitude(new java.math.BigDecimal("-23.5352"));
        entity.setNrLongitude(new java.math.BigDecimal("-46.6353"));

        return entity;
    }

    @Test
    void listsAndSerializesOnlyTheResourceFields() throws Exception {
        when(estacaoDAO.listar()).thenReturn(List.of(existente()));
        mvc.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$[0].idEstacao").value(1))
                .andExpect(jsonPath("$[0].nmEstacao").value("Luz"))

                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void emptyListIsAnArrayWithStatus200() throws Exception {
        when(estacaoDAO.listar()).thenReturn(List.of());
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void findsById() throws Exception {
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existente()));
        mvc.perform(get(BASE + "/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.idEstacao").value(1))
                .andExpect(jsonPath("$.nmEstacao").value("Luz"));
    }

    @Test
    void createsWith201LocationAndConvertedInput() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isCreated()).andExpect(header().string("Location", BASE + "/1"))
                .andExpect(jsonPath("$.idEstacao").value(1))
                .andExpect(jsonPath("$.nmEstacao").value("Luz"))

                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
        var captor = ArgumentCaptor.forClass(Estacao.class);
        verify(estacaoDAO).inserir(captor.capture());
        assertThat(captor.getValue().getIdEstacao()).isEqualTo(1L);

    }

    @Test
    void updatesWithOmittedBodyIdAndPreservesIdentity() throws Exception {
        var entity = existente();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(entity));
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("{\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":0,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idEstacao").value(1));
        verify(estacaoDAO).atualizar(entity);
        verify(estacaoDAO, never()).inserir(any());
    }

    @Test
    void deletesWith204AndNoBody() throws Exception {
        var entity = existente();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(entity));
        mvc.perform(delete(BASE + "/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(estacaoDAO).excluir(entity);
    }

    @Test
    void missingRecordsReturn404ForGetPutAndDelete() throws Exception {
        mvc.perform(get(BASE + "/2")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.caminho").value(BASE + "/2"));
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/2")).andExpect(status().isNotFound());
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
        verify(estacaoDAO, never()).excluir(any());
    }

    @Test
    void invalidPostAndPutUseModelValidationAndDoNotWrite() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"idEstacao\":1,\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":-1,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        var existing = existente();
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existing));
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("{\"idEstacao\":1,\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":-1,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}"))
                .andExpect(status().isBadRequest());
        assertThat(existing.getNmEstacao()).isEqualTo("Luz");
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void createRequiresIdAndUpdateRejectsDifferentId() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":0,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("{\"idEstacao\":2,\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":0,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(estacaoDAO);
    }

    @Test
    void duplicateIdReturns409WithoutOverwrite() throws Exception {
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existente()));
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.caminho").value(BASE));
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
    }

    @Test
    void malformedJsonAndFractionalIdsReturn400() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"idEstacao\":1.5,\"nmEstacao\":\"Luz\",\"stAcessivel\":\"S\",\"nrMinBaldeacao\":0,\"nrLatitude\":-23.5352,\"nrLongitude\":-46.6353}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(estacaoDAO);
    }

    @Test
    void missingOrNullBodyReturns400() throws Exception {
        mvc.perform(post(BASE).contentType(APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content("null"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(estacaoDAO);
    }

    @Test
    void integrityRaceReturnsGeneric409WithoutSqlDetails() throws Exception {
        var sql = new SQLException("ORA-00001 simulated schema and SQL detail", "23000", 1);
        var failure = new DataIntegrityViolationException("insert into simulated SQL", sql);
        doThrow(failure).when(estacaoDAO).inserir(any());
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.mensagem").value(containsString("integridade")))
                .andExpect(content().string(not(containsString("ORA-"))))
                .andExpect(content().string(not(containsString("insert into"))));
        assertThat(failure.getCause()).isSameAs(sql);
    }

    @Test
    void unexpectedFailureReturnsGeneric500() throws Exception {
        when(estacaoDAO.listar()).thenThrow(new DataAccessResourceFailureException("simulated connection detail"));
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
    void dependenciesBlockDeleteWith409() throws Exception {
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existente()));
        when(estacaoDAO.possuiDependencias(1L)).thenReturn(true);
        mvc.perform(delete(BASE + "/1")).andExpect(status().isConflict());
        verify(estacaoDAO, never()).excluir(any());
    }

    @Test
    void uniqueValueConflictReturns409OnPostAndPut() throws Exception {
        when(estacaoDAO.existeNome("Luz", null)).thenReturn(true);
        mvc.perform(post(BASE).contentType(APPLICATION_JSON).content(JSON_VALIDO)).andExpect(status().isConflict());
        when(estacaoDAO.buscarPorId(1L)).thenReturn(Optional.of(existente()));
        when(estacaoDAO.existeNome("Luz", 1L)).thenReturn(true);
        mvc.perform(put(BASE + "/1").contentType(APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict());
        verify(estacaoDAO, never()).inserir(any());
        verify(estacaoDAO, never()).atualizar(any());
    }
}
