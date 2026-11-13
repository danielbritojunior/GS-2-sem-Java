package br.com.fiap.embarqueCertoAPI.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_LINHA", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_LINHA_NUMERO", columnNames = {"NR_LINHA"})
})
public class Linha {

    @NotNull
    @Id
    @Digits(integer = 3, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_LINHA", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Long idLinha;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_OPERADORA", referencedColumnName = "ID_OPERADORA", nullable = false,
            columnDefinition = "NUMBER(3)", foreignKey = @ForeignKey(name = "FK_EC_LINHA_OPERADORA"))
    private Operadora operadora;

    @NotNull
    @Min(1)
    @Max(99)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_LINHA", nullable = false, precision = 2, scale = 0, columnDefinition = "NUMBER(2)")
    private Integer nrLinha;

    @NotNull
    @Size(min = 1, max = 30)
    @Column(name = "NM_LINHA", nullable = false, length = 30, columnDefinition = "VARCHAR2(30)")
    private String nmLinha;

    @NotNull
    @Size(min = 1, max = 10)
    @Pattern(regexp = "METRO|TREM|MONOTRILHO|BRT")
    @Column(name = "TP_MODAL", nullable = false, length = 10, columnDefinition = "VARCHAR2(10)")
    private String tpModal;

    @Size(max = 7)
    @Column(name = "DS_COR_HEX", nullable = true, length = 7, columnDefinition = "VARCHAR2(7)")
    private String dsCorHex;

    public Linha() {
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public void setIdLinha(Long idLinha) {
        this.idLinha = idLinha;
    }

    public Operadora getOperadora() {
        return operadora;
    }

    public void setOperadora(Operadora operadora) {
        this.operadora = operadora;
    }

    public Integer getNrLinha() {
        return nrLinha;
    }

    public void setNrLinha(Integer nrLinha) {
        this.nrLinha = nrLinha;
    }

    public String getNmLinha() {
        return nmLinha;
    }

    public void setNmLinha(String nmLinha) {
        this.nmLinha = nmLinha;
    }

    public String getTpModal() {
        return tpModal;
    }

    public void setTpModal(String tpModal) {
        this.tpModal = tpModal;
    }

    public String getDsCorHex() {
        return dsCorHex;
    }

    public void setDsCorHex(String dsCorHex) {
        this.dsCorHex = dsCorHex;
    }
}
