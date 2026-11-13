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
public class LinhaEstacaoId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Digits(integer = 3, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_LINHA", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Long idLinha;

    @NotNull
    @Digits(integer = 5, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_ESTACAO", nullable = false, precision = 5, scale = 0, columnDefinition = "NUMBER(5)")
    private Long idEstacao;

    public LinhaEstacaoId() {
    }

    public LinhaEstacaoId(Long idLinha, Long idEstacao) {
        this.idLinha = idLinha;
        this.idEstacao = idEstacao;
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public void setIdLinha(Long idLinha) {
        this.idLinha = idLinha;
    }

    public Long getIdEstacao() {
        return idEstacao;
    }

    public void setIdEstacao(Long idEstacao) {
        this.idEstacao = idEstacao;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        LinhaEstacaoId that = (LinhaEstacaoId) other;
        return Objects.equals(idLinha, that.idLinha)
                && Objects.equals(idEstacao, that.idEstacao);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idLinha, idEstacao);
    }
}
