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
@Table(name = "T_EC_TIPO_OCORRENCIA", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_TIPO_OCORRENCIA_NOME", columnNames = {"NM_TIPO"})
})
public class TipoOcorrencia {

    @NotNull
    @Id
    @Digits(integer = 3, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_TIPO_OCORRENCIA", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Long idTipoOcorrencia;

    @NotNull
    @Size(min = 1, max = 50)
    @Column(name = "NM_TIPO", nullable = false, length = 50, columnDefinition = "VARCHAR2(50)")
    private String nmTipo;

    @Size(max = 200)
    @Column(name = "DS_TIPO", nullable = true, length = 200, columnDefinition = "VARCHAR2(200)")
    private String dsTipo;

    @NotNull
    @Pattern(regexp = "S|N")
    @Size(min = 1, max = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ST_AFETA_ACESSIBILIDADE", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String stAfetaAcessibilidade = "N";

    public TipoOcorrencia() {
    }

    public Long getIdTipoOcorrencia() {
        return idTipoOcorrencia;
    }

    public void setIdTipoOcorrencia(Long idTipoOcorrencia) {
        this.idTipoOcorrencia = idTipoOcorrencia;
    }

    public String getNmTipo() {
        return nmTipo;
    }

    public void setNmTipo(String nmTipo) {
        this.nmTipo = nmTipo;
    }

    public String getDsTipo() {
        return dsTipo;
    }

    public void setDsTipo(String dsTipo) {
        this.dsTipo = dsTipo;
    }

    public String getStAfetaAcessibilidade() {
        return stAfetaAcessibilidade;
    }

    public void setStAfetaAcessibilidade(String stAfetaAcessibilidade) {
        this.stAfetaAcessibilidade = stAfetaAcessibilidade;
    }
}
