package br.com.fiap.embarqueCertoAPI.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(name = "T_EC_USUARIO", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_USUARIO_EMAIL", columnNames = {"DS_EMAIL"})
})
public class Usuario {

    // Oracle DATE inclui horario: LocalDateTime usa JDBC TIMESTAMP para preserva-lo.

    @NotNull
    @Id
    @Digits(integer = 8, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_USUARIO", nullable = false, precision = 8, scale = 0, columnDefinition = "NUMBER(8)")
    private Long idUsuario;

    @NotNull
    @Size(min = 1, max = 80)
    @Column(name = "NM_USUARIO", nullable = false, length = 80, columnDefinition = "VARCHAR2(80)")
    private String nmUsuario;

    @NotNull
    @Size(min = 1, max = 120)
    @Pattern(regexp = "(?s).+@.+\\..+")
    @Column(name = "DS_EMAIL", nullable = false, length = 120, columnDefinition = "VARCHAR2(120)")
    private String dsEmail;

    @NotNull
    @Pattern(regexp = "S|N")
    @Size(min = 1, max = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ST_MOBILIDADE_REDUZIDA", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String stMobilidadeReduzida = "N";

    @NotNull
    @Min(0)
    @Max(100)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_REPUTACAO", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Integer nrReputacao = 50;

    @NotNull
    @Column(name = "DT_CADASTRO", nullable = false, columnDefinition = "DATE")
    private LocalDateTime dtCadastro;

    public Usuario() {
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNmUsuario() {
        return nmUsuario;
    }

    public void setNmUsuario(String nmUsuario) {
        this.nmUsuario = nmUsuario;
    }

    public String getDsEmail() {
        return dsEmail;
    }

    public void setDsEmail(String dsEmail) {
        this.dsEmail = dsEmail;
    }

    public String getStMobilidadeReduzida() {
        return stMobilidadeReduzida;
    }

    public void setStMobilidadeReduzida(String stMobilidadeReduzida) {
        this.stMobilidadeReduzida = stMobilidadeReduzida;
    }

    public Integer getNrReputacao() {
        return nrReputacao;
    }

    public void setNrReputacao(Integer nrReputacao) {
        this.nrReputacao = nrReputacao;
    }

    public LocalDateTime getDtCadastro() {
        return dtCadastro;
    }

    public void setDtCadastro(LocalDateTime dtCadastro) {
        this.dtCadastro = dtCadastro;
    }
}
