package br.com.fiap.embarqueCertoAPI.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_OPERADORA", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_OPERADORA_NOME", columnNames = {"NM_OPERADORA"}),
        @UniqueConstraint(name = "UN_EC_OPERADORA_SIGLA", columnNames = {"SG_OPERADORA"})
})
public class Operadora {

    @NotNull
    @Id
    @Digits(integer = 3, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_OPERADORA", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Long idOperadora;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(name = "NM_OPERADORA", nullable = false, length = 80, columnDefinition = "VARCHAR2(80)")
    private String nmOperadora;

    @NotNull
    @Size(min = 1, max = 15)
    @Column(name = "SG_OPERADORA", nullable = false, length = 15, columnDefinition = "VARCHAR2(15)")
    private String sgOperadora;

    @NotNull
    @Size(min = 1, max = 10)
    @Pattern(regexp = "PUBLICA|CONCESSAO")
    @Column(name = "TP_GESTAO", nullable = false, length = 10, columnDefinition = "VARCHAR2(10)")
    private String tpGestao;

    @Size(max = 100)
    @Column(name = "DS_SITE", nullable = true, length = 100, columnDefinition = "VARCHAR2(100)")
    private String dsSite;

    public Operadora() {
    }

    public Long getIdOperadora() {
        return idOperadora;
    }

    public void setIdOperadora(Long idOperadora) {
        this.idOperadora = idOperadora;
    }

    public String getNmOperadora() {
        return nmOperadora;
    }

    public void setNmOperadora(String nmOperadora) {
        this.nmOperadora = nmOperadora;
    }

    public String getSgOperadora() {
        return sgOperadora;
    }

    public void setSgOperadora(String sgOperadora) {
        this.sgOperadora = sgOperadora;
    }

    public String getTpGestao() {
        return tpGestao;
    }

    public void setTpGestao(String tpGestao) {
        this.tpGestao = tpGestao;
    }

    public String getDsSite() {
        return dsSite;
    }

    public void setDsSite(String dsSite) {
        this.dsSite = dsSite;
    }
}
