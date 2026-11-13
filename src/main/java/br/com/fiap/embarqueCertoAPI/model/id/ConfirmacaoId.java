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
public class ConfirmacaoId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Digits(integer = 10, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_OCORRENCIA", nullable = false, precision = 10, scale = 0, columnDefinition = "NUMBER(10)")
    private Long idOcorrencia;

    @NotNull
    @Digits(integer = 8, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_USUARIO", nullable = false, precision = 8, scale = 0, columnDefinition = "NUMBER(8)")
    private Long idUsuario;

    public ConfirmacaoId() {
    }

    public ConfirmacaoId(Long idOcorrencia, Long idUsuario) {
        this.idOcorrencia = idOcorrencia;
        this.idUsuario = idUsuario;
    }

    public Long getIdOcorrencia() {
        return idOcorrencia;
    }

    public void setIdOcorrencia(Long idOcorrencia) {
        this.idOcorrencia = idOcorrencia;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        ConfirmacaoId that = (ConfirmacaoId) other;
        return Objects.equals(idOcorrencia, that.idOcorrencia)
                && Objects.equals(idUsuario, that.idUsuario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idOcorrencia, idUsuario);
    }
}
