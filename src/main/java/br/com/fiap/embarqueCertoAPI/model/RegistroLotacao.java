package br.com.fiap.embarqueCertoAPI.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_REGISTRO_LOTACAO")
public class RegistroLotacao {

    // Oracle DATE inclui horario: LocalDateTime usa JDBC TIMESTAMP para preserva-lo.

    @NotNull
    @Id
    @Digits(integer = 10, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_REGISTRO", nullable = false, precision = 10, scale = 0, columnDefinition = "NUMBER(10)")
    private Long idRegistro;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_USUARIO", referencedColumnName = "ID_USUARIO", nullable = false,
            columnDefinition = "NUMBER(8)", foreignKey = @ForeignKey(name = "FK_EC_LOTACAO_USUARIO"))
    private Usuario usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_LINHA", referencedColumnName = "ID_LINHA", nullable = false,
            columnDefinition = "NUMBER(3)", foreignKey = @ForeignKey(name = "FK_EC_LOTACAO_LINHA"))
    private Linha linha;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ESTACAO", referencedColumnName = "ID_ESTACAO", nullable = false,
            columnDefinition = "NUMBER(5)", foreignKey = @ForeignKey(name = "FK_EC_LOTACAO_ESTACAO"))
    private Estacao estacao;

    @NotNull
    @Min(1)
    @Max(5)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_NIVEL", nullable = false, precision = 1, scale = 0, columnDefinition = "NUMBER(1)")
    private Integer nrNivel;

    @NotNull
    @Size(min = 1, max = 40)
    @Column(name = "DS_SENTIDO", nullable = false, length = 40, columnDefinition = "VARCHAR2(40)")
    private String dsSentido;

    @NotNull
    @Column(name = "DT_REGISTRO", nullable = false, columnDefinition = "DATE")
    private LocalDateTime dtRegistro;

    public RegistroLotacao() {
    }

    public Long getIdRegistro() {
        return idRegistro;
    }

    public void setIdRegistro(Long idRegistro) {
        this.idRegistro = idRegistro;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
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

    public Integer getNrNivel() {
        return nrNivel;
    }

    public void setNrNivel(Integer nrNivel) {
        this.nrNivel = nrNivel;
    }

    public String getDsSentido() {
        return dsSentido;
    }

    public void setDsSentido(String dsSentido) {
        this.dsSentido = dsSentido;
    }

    public LocalDateTime getDtRegistro() {
        return dtRegistro;
    }

    public void setDtRegistro(LocalDateTime dtRegistro) {
        this.dtRegistro = dtRegistro;
    }
}
