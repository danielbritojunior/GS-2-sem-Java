package br.com.fiap.embarqueCertoAPI.model.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class AlertaId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Digits(integer = 10, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_TRAJETO", nullable = false, precision = 10, scale = 0, columnDefinition = "NUMBER(10)")
    private Long idTrajeto;

    @NotNull
    @Digits(integer = 10, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_OCORRENCIA", nullable = false, precision = 10, scale = 0, columnDefinition = "NUMBER(10)")
    private Long idOcorrencia;

    public AlertaId() {
    }

    public AlertaId(Long idTrajeto, Long idOcorrencia) {
        this.idTrajeto = idTrajeto;
        this.idOcorrencia = idOcorrencia;
    }

    public Long getIdTrajeto() {
        return idTrajeto;
    }

    public void setIdTrajeto(Long idTrajeto) {
        this.idTrajeto = idTrajeto;
    }

    public Long getIdOcorrencia() {
        return idOcorrencia;
    }

    public void setIdOcorrencia(Long idOcorrencia) {
        this.idOcorrencia = idOcorrencia;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        AlertaId that = (AlertaId) other;
        return Objects.equals(idTrajeto, that.idTrajeto)
                && Objects.equals(idOcorrencia, that.idOcorrencia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idTrajeto, idOcorrencia);
    }
}
