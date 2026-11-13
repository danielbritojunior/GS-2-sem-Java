package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.model.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

abstract class BoTestSupport {

    private static ValidatorFactory factory;
    protected static Validator validator;
    protected static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:30:45.123456Z"),
            ZoneId.of("America/Sao_Paulo"));
    protected static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 12, 30, 45);
    protected static final LocalDateTime BEFORE = NOW.minusDays(1);

    @BeforeAll
    static void startValidation() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidation() {
        factory.close();
    }

    protected static Operadora operadora() {
        var value = new Operadora();
        value.setIdOperadora(1L);
        value.setNmOperadora("Operadora do banco");
        return value;
    }

    protected static TipoOcorrencia tipoOcorrencia() {
        var value = new TipoOcorrencia();
        value.setIdTipoOcorrencia(1L);
        return value;
    }

    protected static Usuario usuario() {
        var value = new Usuario();
        value.setIdUsuario(1L);
        return value;
    }

    protected static Estacao estacao() {
        var value = new Estacao();
        value.setIdEstacao(1L);
        value.setNmEstacao("Luz");
        value.setNrLatitude(new BigDecimal("-23.535200"));
        value.setNrLongitude(new BigDecimal("-46.635300"));
        return value;
    }

    protected static Linha linha() {
        var value = new Linha();
        value.setIdLinha(1L);
        value.setOperadora(operadora());
        value.setNrLinha(1);
        value.setNmLinha("Azul");
        value.setTpModal("METRO");
        return value;
    }

    protected static Ocorrencia ocorrencia() {
        var value = new Ocorrencia();
        value.setIdOcorrencia(1L);
        value.setTipoOcorrencia(tipoOcorrencia());
        value.setLinha(linha());
        value.setEstacao(estacao());
        value.setUsuario(usuario());
        value.setTpFonte("USUARIO");
        value.setDsOcorrencia("Atraso");
        return value;
    }

    protected static RegistroLotacao registroLotacao() {
        var value = new RegistroLotacao();
        value.setIdRegistro(1L);
        value.setUsuario(usuario());
        value.setLinha(linha());
        value.setEstacao(estacao());
        value.setNrNivel(3);
        value.setDsSentido("Centro");
        return value;
    }
}
