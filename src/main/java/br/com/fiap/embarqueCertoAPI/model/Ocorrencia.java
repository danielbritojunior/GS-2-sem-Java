package br.com.fiap.embarqueCertoAPI.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_OCORRENCIA")
public class Ocorrencia {

    // Oracle DATE inclui horario: LocalDateTime usa JDBC TIMESTAMP para preserva-lo.

    @NotNull
    @Id
    @Digits(integer = 10, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_OCORRENCIA", nullable = false, precision = 10, scale = 0, columnDefinition = "NUMBER(10)")
    private Long idOcorrencia;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_TIPO_OCORRENCIA", referencedColumnName = "ID_TIPO_OCORRENCIA", nullable = false,
            columnDefinition = "NUMBER(3)", foreignKey = @ForeignKey(name = "FK_EC_OCORRENCIA_TIPO"))
    private TipoOcorrencia tipoOcorrencia;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_LINHA", referencedColumnName = "ID_LINHA", nullable = false,
            columnDefinition = "NUMBER(3)", foreignKey = @ForeignKey(name = "FK_EC_OCORRENCIA_LINHA"))
    private Linha linha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_ESTACAO", referencedColumnName = "ID_ESTACAO", nullable = true,
            columnDefinition = "NUMBER(5)", foreignKey = @ForeignKey(name = "FK_EC_OCORRENCIA_ESTACAO"))
    private Estacao estacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_USUARIO", referencedColumnName = "ID_USUARIO", nullable = true,
            columnDefinition = "NUMBER(8)", foreignKey = @ForeignKey(name = "FK_EC_OCORRENCIA_USUARIO"))
    private Usuario usuario;

    @NotNull
    @Size(min = 1, max = 10)
    @Pattern(regexp = "USUARIO|OPERADORA")
    @Column(name = "TP_FONTE", nullable = false, length = 10, columnDefinition = "VARCHAR2(10)")
    private String tpFonte;

    @NotNull
    @Size(min = 1, max = 300)
    @Column(name = "DS_OCORRENCIA", nullable = false, length = 300, columnDefinition = "VARCHAR2(300)")
    private String dsOcorrencia;

    @NotNull
    @Min(0)
    @Max(600)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_ATRASO_MIN", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Integer nrAtrasoMin = 0;

    @NotNull
    @Size(min = 1, max = 10)
    @Pattern(regexp = "ABERTA|CONFIRMADA|ENCERRADA|DESCARTADA")
    @Column(name = "ST_OCORRENCIA", nullable = false, length = 10, columnDefinition = "VARCHAR2(10)")
    private String stOcorrencia = "ABERTA";

    @NotNull
    @Column(name = "DT_INICIO", nullable = false, columnDefinition = "DATE")
    private LocalDateTime dtInicio;

    @Column(name = "DT_FIM", nullable = true, columnDefinition = "DATE")
    private LocalDateTime dtFim;

    public Ocorrencia() {
    }

    public Long getIdOcorrencia() {
        return idOcorrencia;
    }

    public void setIdOcorrencia(Long idOcorrencia) {
        this.idOcorrencia = idOcorrencia;
    }

    public TipoOcorrencia getTipoOcorrencia() {
        return tipoOcorrencia;
    }

    public void setTipoOcorrencia(TipoOcorrencia tipoOcorrencia) {
        this.tipoOcorrencia = tipoOcorrencia;
    }

    public Linha getLinha() {
        return linha;
    }

    public void setLinha(Linha linha) {
        this.linha = linha;
    }

    public Estacao getEstacao() {
        return estacao;
    }

    public void setEstacao(Estacao estacao) {
        this.estacao = estacao;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getTpFonte() {
        return tpFonte;
    }

    public void setTpFonte(String tpFonte) {
        this.tpFonte = tpFonte;
    }

    public String getDsOcorrencia() {
        return dsOcorrencia;
    }

    public void setDsOcorrencia(String dsOcorrencia) {
        this.dsOcorrencia = dsOcorrencia;
    }

    public Integer getNrAtrasoMin() {
        return nrAtrasoMin;
    }

    public void setNrAtrasoMin(Integer nrAtrasoMin) {
        this.nrAtrasoMin = nrAtrasoMin;
    }

    public String getStOcorrencia() {
        return stOcorrencia;
    }

    public void setStOcorrencia(String stOcorrencia) {
        this.stOcorrencia = stOcorrencia;
    }

    public LocalDateTime getDtInicio() {
        return dtInicio;
    }

    public void setDtInicio(LocalDateTime dtInicio) {
        this.dtInicio = dtInicio;
    }

    public LocalDateTime getDtFim() {
        return dtFim;
    }

    public void setDtFim(LocalDateTime dtFim) {
        this.dtFim = dtFim;
    }

    @AssertTrue(message = "A fonte USUARIO exige autor; OPERADORA exige autor ausente")
    public boolean isAutorCompativelComFonte() {
        if ("USUARIO".equals(tpFonte)) {
            return usuario != null;
        }
        if ("OPERADORA".equals(tpFonte)) {
            return usuario == null;
        }
        return true; // Presenca e dominio sao verificados no atributo.
    }

    @AssertTrue(message = "Ocorrencia ativa nao tem fim; encerrada ou descartada exige fim >= inicio")
    public boolean isFimCompativelComSituacao() {
        if ("ABERTA".equals(stOcorrencia) || "CONFIRMADA".equals(stOcorrencia)) {
            return dtFim == null;
        }
        if ("ENCERRADA".equals(stOcorrencia) || "DESCARTADA".equals(stOcorrencia)) {
            return dtFim != null && (dtInicio == null || !dtFim.isBefore(dtInicio));
        }
        return true;
    }
}
