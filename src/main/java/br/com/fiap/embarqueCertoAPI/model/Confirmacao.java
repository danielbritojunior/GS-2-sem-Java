package br.com.fiap.embarqueCertoAPI.model;

import br.com.fiap.embarqueCertoAPI.model.id.ConfirmacaoId;
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
@Table(name = "T_EC_CONFIRMACAO")
public class Confirmacao {

    // Oracle DATE inclui horario: LocalDateTime usa JDBC TIMESTAMP para preserva-lo.

    @NotNull
    @Valid
    @EmbeddedId
    private ConfirmacaoId id;

    @NotNull
    @MapsId("idOcorrencia")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_OCORRENCIA", referencedColumnName = "ID_OCORRENCIA", nullable = false,
            columnDefinition = "NUMBER(10)", foreignKey = @ForeignKey(name = "FK_EC_CONFIRMACAO_OCORRENCIA"))
    private Ocorrencia ocorrencia;

    @NotNull
    @MapsId("idUsuario")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_USUARIO", referencedColumnName = "ID_USUARIO", nullable = false,
            columnDefinition = "NUMBER(8)", foreignKey = @ForeignKey(name = "FK_EC_CONFIRMACAO_USUARIO"))
    private Usuario usuario;

    @NotNull
    @Pattern(regexp = "S|N")
    @Size(min = 1, max = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ST_CONFIRMA", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String stConfirma = "S";

    @NotNull
    @Column(name = "DT_CONFIRMACAO", nullable = false, columnDefinition = "DATE")
    private LocalDateTime dtConfirmacao;

    public Confirmacao() {
    }

    public ConfirmacaoId getId() {
        return id;
    }

    public void setId(ConfirmacaoId id) {
        this.id = id;
    }

    public Ocorrencia getOcorrencia() {
        return ocorrencia;
    }

    public void setOcorrencia(Ocorrencia ocorrencia) {
        this.ocorrencia = ocorrencia;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getStConfirma() {
        return stConfirma;
    }

    public void setStConfirma(String stConfirma) {
        this.stConfirma = stConfirma;
    }

    public LocalDateTime getDtConfirmacao() {
        return dtConfirmacao;
    }

    public void setDtConfirmacao(LocalDateTime dtConfirmacao) {
        this.dtConfirmacao = dtConfirmacao;
    }
}
