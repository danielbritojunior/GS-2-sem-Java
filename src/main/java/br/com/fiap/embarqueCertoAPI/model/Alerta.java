package br.com.fiap.embarqueCertoAPI.model;

import br.com.fiap.embarqueCertoAPI.model.id.AlertaId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_ALERTA")
public class Alerta {

    // Oracle DATE inclui horario: LocalDateTime usa JDBC TIMESTAMP para preserva-lo.

    @NotNull
    @Valid
    @EmbeddedId
    private AlertaId id;

    @NotNull
    @MapsId("idTrajeto")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_TRAJETO", referencedColumnName = "ID_TRAJETO", nullable = false,
            columnDefinition = "NUMBER(10)", foreignKey = @ForeignKey(name = "FK_EC_ALERTA_TRAJETO"))
    private Trajeto trajeto;

    @NotNull
    @MapsId("idOcorrencia")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_OCORRENCIA", referencedColumnName = "ID_OCORRENCIA", nullable = false,
            columnDefinition = "NUMBER(10)", foreignKey = @ForeignKey(name = "FK_EC_ALERTA_OCORRENCIA"))
    private Ocorrencia ocorrencia;

    @NotNull
    @Size(min = 1, max = 5)
    @Pattern(regexp = "PUSH|SMS|EMAIL")
    @Column(name = "TP_CANAL", nullable = false, length = 5, columnDefinition = "VARCHAR2(5)")
    private String tpCanal = "PUSH";

    @NotNull
    @Pattern(regexp = "S|N")
    @Size(min = 1, max = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ST_LIDO", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String stLido = "N";

    @NotNull
    @Column(name = "DT_ENVIO", nullable = false, columnDefinition = "DATE")
    private LocalDateTime dtEnvio;

    public Alerta() {
    }

    public AlertaId getId() {
        return id;
    }

    public void setId(AlertaId id) {
        this.id = id;
    }

    public Trajeto getTrajeto() {
        return trajeto;
    }

    public void setTrajeto(Trajeto trajeto) {
        this.trajeto = trajeto;
    }

    public Ocorrencia getOcorrencia() {
        return ocorrencia;
    }

    public void setOcorrencia(Ocorrencia ocorrencia) {
        this.ocorrencia = ocorrencia;
    }

    public String getTpCanal() {
        return tpCanal;
    }

    public void setTpCanal(String tpCanal) {
        this.tpCanal = tpCanal;
    }

    public String getStLido() {
        return stLido;
    }

    public void setStLido(String stLido) {
        this.stLido = stLido;
    }

    public LocalDateTime getDtEnvio() {
        return dtEnvio;
    }

    public void setDtEnvio(LocalDateTime dtEnvio) {
        this.dtEnvio = dtEnvio;
    }
}
