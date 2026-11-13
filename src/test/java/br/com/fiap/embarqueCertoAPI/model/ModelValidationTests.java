package br.com.fiap.embarqueCertoAPI.model;

import br.com.fiap.embarqueCertoAPI.model.id.AlertaId;
import br.com.fiap.embarqueCertoAPI.model.id.ConfirmacaoId;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelValidationTests {

    private static ValidatorFactory factory;
    private static Validator validator;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 8, 30, 45);

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    record FieldCase(Class<?> type, String property) {}
    record NumericCase(Class<?> type, String property, int min, int max) {}
    record PrecisionCase(Class<?> type, String property, int digits) {}
    record StringCase(Class<?> type, String property, int max) {}
    record DomainCase(Class<?> type, String property, String allowed) {}

    static Map<Class<?>, Object> examples() {
        var operadora = new Operadora();
        operadora.setIdOperadora(1L);
        operadora.setNmOperadora("Operadora");
        operadora.setSgOperadora("OP");
        operadora.setTpGestao("PUBLICA");

        var linha = new Linha();
        linha.setIdLinha(1L);
        linha.setOperadora(operadora);
        linha.setNrLinha(1);
        linha.setNmLinha("Azul");
        linha.setTpModal("METRO");

        var origem = new Estacao();
        origem.setIdEstacao(1L);
        origem.setNmEstacao("Luz");
        origem.setNrLatitude(new BigDecimal("-23.535200"));
        origem.setNrLongitude(new BigDecimal("-46.635300"));
        var destino = new Estacao();
        destino.setIdEstacao(2L);

        var usuario = new Usuario();
        usuario.setIdUsuario(1L);
        usuario.setNmUsuario("Passageiro");
        usuario.setDsEmail("passageiro@example.com");
        usuario.setDtCadastro(NOW);

        var tipo = new TipoOcorrencia();
        tipo.setIdTipoOcorrencia(1L);
        tipo.setNmTipo("Atraso");

        var linhaEstacao = new LinhaEstacao();
        linhaEstacao.setId(new LinhaEstacaoId(1L, 1L));
        linhaEstacao.setLinha(linha);
        linhaEstacao.setEstacao(origem);
        linhaEstacao.setNrOrdem(1);

        var ocorrencia = new Ocorrencia();
        ocorrencia.setIdOcorrencia(1L);
        ocorrencia.setTipoOcorrencia(tipo);
        ocorrencia.setLinha(linha);
        ocorrencia.setTpFonte("USUARIO");
        ocorrencia.setUsuario(usuario);
        ocorrencia.setDsOcorrencia("Atraso na linha");
        ocorrencia.setDtInicio(NOW);

        var confirmacao = new Confirmacao();
        confirmacao.setId(new ConfirmacaoId(1L, 1L));
        confirmacao.setOcorrencia(ocorrencia);
        confirmacao.setUsuario(usuario);
        confirmacao.setDtConfirmacao(NOW);

        var lotacao = new RegistroLotacao();
        lotacao.setIdRegistro(1L);
        lotacao.setUsuario(usuario);
        lotacao.setLinha(linha);
        lotacao.setEstacao(origem);
        lotacao.setNrNivel(3);
        lotacao.setDsSentido("Centro");
        lotacao.setDtRegistro(NOW);

        var trajeto = new Trajeto();
        trajeto.setIdTrajeto(1L);
        trajeto.setUsuario(usuario);
        trajeto.setEstacaoOrigem(origem);
        trajeto.setEstacaoDestino(destino);
        trajeto.setNmTrajeto("Trabalho");
        trajeto.setHrPartida("08:30");

        var alerta = new Alerta();
        alerta.setId(new AlertaId(1L, 1L));
        alerta.setTrajeto(trajeto);
        alerta.setOcorrencia(ocorrencia);
        alerta.setDtEnvio(NOW);

        var result = new LinkedHashMap<Class<?>, Object>();
        for (Object entity : new Object[]{operadora, linha, origem, usuario, tipo, linhaEstacao,
                ocorrencia, confirmacao, lotacao, trajeto, alerta}) {
            result.put(entity.getClass(), entity);
        }
        return result;
    }

    static Stream<Object> validEntities() {
        return examples().values().stream();
    }

    private static <T> T sample(Class<T> type) {
        return type.cast(examples().get(type));
    }

    private static void set(Object bean, String property, Object value) throws Exception {
        var field = bean.getClass().getDeclaredField(property);
        field.setAccessible(true);
        field.set(bean, value);
    }

    private static void validProperty(Object bean, String property, Object value) throws Exception {
        set(bean, property, value);
        assertThat(validator.validateProperty(bean, property)).as(property + " = " + value).isEmpty();
    }

    private static void invalidProperty(Object bean, String property, Object value) throws Exception {
        set(bean, property, value);
        assertThat(validator.validateProperty(bean, property)).as(property + " = " + value).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("validEntities")
    void acceptsCompleteEntitiesAndTheirDefaults(Object entity) {
        assertThat(validator.validate(entity)).isEmpty();
    }

    static Stream<FieldCase> requiredFields() {
        Map<Class<?>, String> required = Map.ofEntries(
                Map.entry(Operadora.class, "idOperadora nmOperadora sgOperadora tpGestao"),
                Map.entry(Linha.class, "idLinha operadora nrLinha nmLinha tpModal"),
                Map.entry(Estacao.class, "idEstacao nmEstacao stAcessivel nrMinBaldeacao nrLatitude nrLongitude"),
                Map.entry(Usuario.class, "idUsuario nmUsuario dsEmail stMobilidadeReduzida nrReputacao dtCadastro"),
                Map.entry(TipoOcorrencia.class, "idTipoOcorrencia nmTipo stAfetaAcessibilidade"),
                Map.entry(LinhaEstacao.class, "id linha estacao nrOrdem"),
                Map.entry(Ocorrencia.class, "idOcorrencia tipoOcorrencia linha tpFonte dsOcorrencia nrAtrasoMin stOcorrencia dtInicio"),
                Map.entry(Confirmacao.class, "id ocorrencia usuario stConfirma dtConfirmacao"),
                Map.entry(RegistroLotacao.class, "idRegistro usuario linha estacao nrNivel dsSentido dtRegistro"),
                Map.entry(Trajeto.class, "idTrajeto usuario estacaoOrigem estacaoDestino nmTrajeto hrPartida stAlertaAtivo"),
                Map.entry(Alerta.class, "id trajeto ocorrencia tpCanal stLido dtEnvio"));
        return required.entrySet().stream().flatMap(entry -> Stream.of(entry.getValue().split(" "))
                .map(property -> new FieldCase(entry.getKey(), property)));
    }

    @ParameterizedTest
    @MethodSource("requiredFields")
    void rejectsEachMissingRequiredAttribute(FieldCase field) throws Exception {
        invalidProperty(sample(field.type()), field.property(), null);
    }

    static Stream<FieldCase> optionalFields() {
        return Stream.of(new FieldCase(Operadora.class, "dsSite"), new FieldCase(Linha.class, "dsCorHex"),
                new FieldCase(TipoOcorrencia.class, "dsTipo"), new FieldCase(LinhaEstacao.class, "nrMinProxima"),
                new FieldCase(Ocorrencia.class, "estacao"), new FieldCase(Ocorrencia.class, "usuario"),
                new FieldCase(Ocorrencia.class, "dtFim"));
    }

    @ParameterizedTest
    @MethodSource("optionalFields")
    void acceptsNullOptionalAttributes(FieldCase field) throws Exception {
        validProperty(sample(field.type()), field.property(), null);
    }

    static Stream<StringCase> textFields() {
        return Stream.of(
                new StringCase(Operadora.class, "nmOperadora", 80), new StringCase(Operadora.class, "sgOperadora", 15),
                new StringCase(Operadora.class, "dsSite", 100), new StringCase(Linha.class, "nmLinha", 30),
                new StringCase(Linha.class, "dsCorHex", 7), new StringCase(Estacao.class, "nmEstacao", 60),
                new StringCase(Usuario.class, "nmUsuario", 80), new StringCase(TipoOcorrencia.class, "nmTipo", 50),
                new StringCase(TipoOcorrencia.class, "dsTipo", 200), new StringCase(Ocorrencia.class, "dsOcorrencia", 300),
                new StringCase(RegistroLotacao.class, "dsSentido", 40), new StringCase(Trajeto.class, "nmTrajeto", 40));
    }

    @ParameterizedTest
    @MethodSource("textFields")
    void enforcesColumnTextLengthsWithoutAddingBlankRestrictions(StringCase field) throws Exception {
        Object entity = sample(field.type());
        validProperty(entity, field.property(), "x".repeat(field.max()));
        invalidProperty(entity, field.property(), "x".repeat(field.max() + 1));
        validProperty(entity, field.property(), " ");
        boolean optional = optionalFields().anyMatch(f -> f.type() == field.type() && f.property().equals(field.property()));
        if (optional) {
            validProperty(entity, field.property(), "");
        } else {
            invalidProperty(entity, field.property(), "");
        }
    }

    static Stream<NumericCase> integers() {
        return Stream.of(new NumericCase(Linha.class, "nrLinha", 1, 99),
                new NumericCase(Estacao.class, "nrMinBaldeacao", 0, 99),
                new NumericCase(LinhaEstacao.class, "nrOrdem", 1, 999),
                new NumericCase(LinhaEstacao.class, "nrMinProxima", 1, 999),
                new NumericCase(Usuario.class, "nrReputacao", 0, 100),
                new NumericCase(Ocorrencia.class, "nrAtrasoMin", 0, 600),
                new NumericCase(RegistroLotacao.class, "nrNivel", 1, 5));
    }

    @ParameterizedTest
    @MethodSource("integers")
    void enforcesBothIntegerBoundaries(NumericCase field) throws Exception {
        Object bean = sample(field.type());
        validProperty(bean, field.property(), field.min());
        validProperty(bean, field.property(), field.max());
        invalidProperty(bean, field.property(), field.min() - 1);
        invalidProperty(bean, field.property(), field.max() + 1);
    }

    static Stream<PrecisionCase> identifiers() {
        return Stream.of(new PrecisionCase(Operadora.class, "idOperadora", 3),
                new PrecisionCase(Linha.class, "idLinha", 3), new PrecisionCase(Estacao.class, "idEstacao", 5),
                new PrecisionCase(Usuario.class, "idUsuario", 8), new PrecisionCase(TipoOcorrencia.class, "idTipoOcorrencia", 3),
                new PrecisionCase(Ocorrencia.class, "idOcorrencia", 10),
                new PrecisionCase(RegistroLotacao.class, "idRegistro", 10), new PrecisionCase(Trajeto.class, "idTrajeto", 10),
                new PrecisionCase(LinhaEstacaoId.class, "idLinha", 3), new PrecisionCase(LinhaEstacaoId.class, "idEstacao", 5),
                new PrecisionCase(ConfirmacaoId.class, "idOcorrencia", 10), new PrecisionCase(ConfirmacaoId.class, "idUsuario", 8),
                new PrecisionCase(AlertaId.class, "idTrajeto", 10), new PrecisionCase(AlertaId.class, "idOcorrencia", 10));
    }

    @ParameterizedTest
    @MethodSource("identifiers")
    void validatesIdPrecisionWithoutRequiringPositivity(PrecisionCase field) throws Exception {
        Object bean = field.type().getConstructor().newInstance();
        long overflow = Long.parseLong("1" + "0".repeat(field.digits()));
        validProperty(bean, field.property(), 0L);
        validProperty(bean, field.property(), overflow - 1);
        validProperty(bean, field.property(), -(overflow - 1));
        invalidProperty(bean, field.property(), overflow);
        invalidProperty(bean, field.property(), -overflow);
        invalidProperty(bean, field.property(), null);
    }

    static Stream<DomainCase> domains() {
        return Stream.of(new DomainCase(Operadora.class, "tpGestao", "PUBLICA,CONCESSAO"),
                new DomainCase(Linha.class, "tpModal", "METRO,TREM,MONOTRILHO,BRT"),
                new DomainCase(Ocorrencia.class, "tpFonte", "USUARIO,OPERADORA"),
                new DomainCase(Ocorrencia.class, "stOcorrencia", "ABERTA,CONFIRMADA,ENCERRADA,DESCARTADA"),
                new DomainCase(Alerta.class, "tpCanal", "PUSH,SMS,EMAIL"),
                new DomainCase(Estacao.class, "stAcessivel", "S,N"),
                new DomainCase(Usuario.class, "stMobilidadeReduzida", "S,N"),
                new DomainCase(TipoOcorrencia.class, "stAfetaAcessibilidade", "S,N"),
                new DomainCase(Confirmacao.class, "stConfirma", "S,N"),
                new DomainCase(Trajeto.class, "stAlertaAtivo", "S,N"),
                new DomainCase(Alerta.class, "stLido", "S,N"));
    }

    @ParameterizedTest
    @MethodSource("domains")
    void enforcesExactDomainsAndFlags(DomainCase field) throws Exception {
        Object bean = sample(field.type());
        for (String allowed : field.allowed().split(",")) {
            validProperty(bean, field.property(), allowed);
            invalidProperty(bean, field.property(), allowed.toLowerCase());
            invalidProperty(bean, field.property(), allowed + " ");
        }
        invalidProperty(bean, field.property(), "X");
        invalidProperty(bean, field.property(), "");
    }

    @ParameterizedTest
    @CsvSource({"nrLatitude,-90,90", "nrLongitude,-180,180"})
    void enforcesCoordinateRangeAndScale(String property, String min, String max) throws Exception {
        Object bean = sample(Estacao.class);
        validProperty(bean, property, new BigDecimal(min));
        validProperty(bean, property, new BigDecimal(max));
        validProperty(bean, property, new BigDecimal("-23.123456"));
        invalidProperty(bean, property, new BigDecimal(min).subtract(new BigDecimal("0.000001")));
        invalidProperty(bean, property, new BigDecimal(max).add(new BigDecimal("0.000001")));
        invalidProperty(bean, property, new BigDecimal("1.1234567"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b.c", "nome.sobrenome+tag@example.com", "a b@c.d", "a@@b.c"})
    void acceptsEmailAccordingToSqlLikeCheck(String email) throws Exception {
        validProperty(sample(Usuario.class), "dsEmail", email);
    }

    @ParameterizedTest
    @ValueSource(strings = {"@b.c", "a@.c", "a@b.", "abc", "", "a@b"})
    void rejectsEmailMissingSqlCheckComponents(String email) throws Exception {
        invalidProperty(sample(Usuario.class), "dsEmail", email);
    }

    @Test
    void enforcesEmailColumnLength() throws Exception {
        var bean = sample(Usuario.class);
        validProperty(bean, "dsEmail", "x".repeat(116) + "@b.c");
        invalidProperty(bean, "dsEmail", "x".repeat(117) + "@b.c");
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "23:59", "09:05"})
    void acceptsValidDepartureTime(String time) throws Exception {
        validProperty(sample(Trajeto.class), "hrPartida", time);
    }

    @ParameterizedTest
    @ValueSource(strings = {"24:00", "23:60", "9:05", "09:5", "09:05:00", " 09:05", "", "09:05\n"})
    void rejectsInvalidDepartureTime(String time) throws Exception {
        invalidProperty(sample(Trajeto.class), "hrPartida", time);
    }

    @Test
    void validatesAuthorAgainstSource() {
        var ocorrencia = sample(Ocorrencia.class);
        ocorrencia.setUsuario(null);
        assertThat(validator.validate(ocorrencia)).extracting(v -> v.getPropertyPath().toString())
                .contains("autorCompativelComFonte");
        ocorrencia.setTpFonte("OPERADORA");
        assertThat(validator.validate(ocorrencia)).isEmpty();
        ocorrencia.setUsuario(new Usuario());
        assertThat(validator.validate(ocorrencia)).extracting(v -> v.getPropertyPath().toString())
                .contains("autorCompativelComFonte");
        ocorrencia.setTpFonte("USUARIO");
        assertThat(validator.validate(ocorrencia)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABERTA", "CONFIRMADA"})
    void activeOccurrenceRequiresNoEnd(String status) {
        var bean = sample(Ocorrencia.class);
        bean.setStOcorrencia(status);
        assertThat(validator.validate(bean)).isEmpty();
        bean.setDtFim(NOW);
        assertThat(validator.validate(bean)).extracting(v -> v.getPropertyPath().toString())
                .contains("fimCompativelComSituacao");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENCERRADA", "DESCARTADA"})
    void closedOccurrenceRequiresEndNotEarlierThanStart(String status) {
        var bean = sample(Ocorrencia.class);
        bean.setStOcorrencia(status);
        assertThat(validator.validate(bean)).extracting(v -> v.getPropertyPath().toString())
                .contains("fimCompativelComSituacao");
        bean.setDtFim(NOW.minusSeconds(1));
        assertThat(validator.validate(bean)).isNotEmpty();
        bean.setDtFim(NOW);
        assertThat(validator.validate(bean)).isEmpty();
        bean.setDtFim(NOW.plusSeconds(1));
        assertThat(validator.validate(bean)).isEmpty();
    }

    @Test
    void comparesStationIdsInsteadOfObjectIdentity() {
        var bean = sample(Trajeto.class);
        var sameIdDifferentInstance = new Estacao();
        sameIdDifferentInstance.setIdEstacao(bean.getEstacaoOrigem().getIdEstacao());
        bean.setEstacaoDestino(sameIdDifferentInstance);
        assertThat(validator.validate(bean)).extracting(v -> v.getPropertyPath().toString())
                .contains("estacoesDiferentes");
        sameIdDifferentInstance.setIdEstacao(2L);
        assertThat(validator.validate(bean)).isEmpty();
    }

    @Test
    void validatesCompositeIdComponentsRecursively() {
        var linhaEstacao = sample(LinhaEstacao.class);
        linhaEstacao.setId(new LinhaEstacaoId(null, 100_000L));
        assertThat(validator.validate(linhaEstacao)).extracting(v -> v.getPropertyPath().toString())
                .contains("id.idLinha", "id.idEstacao");
        var confirmacao = sample(Confirmacao.class);
        confirmacao.setId(new ConfirmacaoId(10_000_000_000L, null));
        assertThat(validator.validate(confirmacao)).extracting(v -> v.getPropertyPath().toString())
                .contains("id.idOcorrencia", "id.idUsuario");
        var alerta = sample(Alerta.class);
        alerta.setId(new AlertaId(null, 10_000_000_000L));
        assertThat(validator.validate(alerta)).extracting(v -> v.getPropertyPath().toString())
                .contains("id.idTrajeto", "id.idOcorrencia");
    }

    @Test
    void doesNotRecursivelyValidateRelatedEntities() {
        var linha = sample(Linha.class);
        linha.setOperadora(new Operadora());
        assertThat(validator.validate(linha)).isEmpty();
        var trajeto = sample(Trajeto.class);
        trajeto.setEstacaoDestino(new Estacao());
        assertThat(validator.validate(trajeto)).isEmpty();
    }

    @Test
    void initializesOnlyConstantDefaultsAndLeavesDatesForBo() {
        assertThat(new Estacao().getStAcessivel()).isEqualTo("S");
        assertThat(new Estacao().getNrMinBaldeacao()).isZero();
        assertThat(new Usuario().getStMobilidadeReduzida()).isEqualTo("N");
        assertThat(new Usuario().getNrReputacao()).isEqualTo(50);
        assertThat(new TipoOcorrencia().getStAfetaAcessibilidade()).isEqualTo("N");
        assertThat(new Ocorrencia().getNrAtrasoMin()).isZero();
        assertThat(new Ocorrencia().getStOcorrencia()).isEqualTo("ABERTA");
        assertThat(new Confirmacao().getStConfirma()).isEqualTo("S");
        assertThat(new Trajeto().getStAlertaAtivo()).isEqualTo("S");
        assertThat(new Alerta().getTpCanal()).isEqualTo("PUSH");
        assertThat(new Alerta().getStLido()).isEqualTo("N");
        assertThat(new Usuario().getDtCadastro()).isNull();
        assertThat(new Ocorrencia().getDtInicio()).isNull();
        assertThat(new Confirmacao().getDtConfirmacao()).isNull();
        assertThat(new RegistroLotacao().getDtRegistro()).isNull();
        assertThat(new Alerta().getDtEnvio()).isNull();
    }
}
