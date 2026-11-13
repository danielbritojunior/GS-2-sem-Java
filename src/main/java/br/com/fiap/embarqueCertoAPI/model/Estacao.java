package br.com.fiap.embarqueCertoAPI.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_ESTACAO", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_ESTACAO_NOME", columnNames = {"NM_ESTACAO"})
})
public class Estacao {

    @NotNull
    @Id
    @Digits(integer = 5, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_ESTACAO", nullable = false, precision = 5, scale = 0, columnDefinition = "NUMBER(5)")
    private Long idEstacao;

    @NotNull
    @Size(min = 1, max = 60)
    @Column(name = "NM_ESTACAO", nullable = false, length = 60, columnDefinition = "VARCHAR2(60)")
    private String nmEstacao;

    @NotNull
    @Pattern(regexp = "S|N")
    @Size(min = 1, max = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ST_ACESSIVEL", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String stAcessivel = "S";

    @NotNull
    @Min(0)
    @Max(99)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_MIN_BALDEACAO", nullable = false, precision = 2, scale = 0, columnDefinition = "NUMBER(2)")
    private Integer nrMinBaldeacao = 0;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    @Digits(integer = 3, fraction = 6)
    @Column(name = "NR_LATITUDE", nullable = false, precision = 9, scale = 6, columnDefinition = "NUMBER(9,6)")
    private BigDecimal nrLatitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    @Digits(integer = 3, fraction = 6)
    @Column(name = "NR_LONGITUDE", nullable = false, precision = 9, scale = 6, columnDefinition = "NUMBER(9,6)")
    private BigDecimal nrLongitude;

    public Estacao() {
    }

    public Long getIdEstacao() {
        return idEstacao;
    }

    public void setIdEstacao(Long idEstacao) {
        this.idEstacao = idEstacao;
    }

    public String getNmEstacao() {
        return nmEstacao;
    }

    public void setNmEstacao(String nmEstacao) {
        this.nmEstacao = nmEstacao;
    }

    public String getStAcessivel() {
        return stAcessivel;
    }

    public void setStAcessivel(String stAcessivel) {
        this.stAcessivel = stAcessivel;
    }

    public Integer getNrMinBaldeacao() {
        return nrMinBaldeacao;
    }

    public void setNrMinBaldeacao(Integer nrMinBaldeacao) {
        this.nrMinBaldeacao = nrMinBaldeacao;
    }

    public BigDecimal getNrLatitude() {
        return nrLatitude;
    }

    public void setNrLatitude(BigDecimal nrLatitude) {
        this.nrLatitude = nrLatitude;
    }

    public BigDecimal getNrLongitude() {
        return nrLongitude;
    }

    public void setNrLongitude(BigDecimal nrLongitude) {
        this.nrLongitude = nrLongitude;
    }
}
