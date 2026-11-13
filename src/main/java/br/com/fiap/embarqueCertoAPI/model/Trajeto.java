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
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_TRAJETO", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_TRAJETO_NOME", columnNames = {"ID_USUARIO", "NM_TRAJETO"})
})
public class Trajeto {

    @NotNull
    @Id
    @Digits(integer = 10, fraction = 0)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "ID_TRAJETO", nullable = false, precision = 10, scale = 0, columnDefinition = "NUMBER(10)")
    private Long idTrajeto;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_USUARIO", referencedColumnName = "ID_USUARIO", nullable = false,
            columnDefinition = "NUMBER(8)", foreignKey = @ForeignKey(name = "FK_EC_TRAJETO_USUARIO"))
    private Usuario usuario;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ESTACAO_ORIGEM", referencedColumnName = "ID_ESTACAO", nullable = false,
            columnDefinition = "NUMBER(5)", foreignKey = @ForeignKey(name = "FK_EC_TRAJETO_ORIGEM"))
    private Estacao estacaoOrigem;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ESTACAO_DESTINO", referencedColumnName = "ID_ESTACAO", nullable = false,
            columnDefinition = "NUMBER(5)", foreignKey = @ForeignKey(name = "FK_EC_TRAJETO_DESTINO"))
    private Estacao estacaoDestino;

    @NotNull
    @Size(min = 1, max = 40)
    @Column(name = "NM_TRAJETO", nullable = false, length = 40, columnDefinition = "VARCHAR2(40)")
    private String nmTrajeto;

    @NotNull
    @Size(min = 1, max = 5)
    @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$")
    @Column(name = "HR_PARTIDA", nullable = false, length = 5, columnDefinition = "VARCHAR2(5)")
    private String hrPartida;

    @NotNull
    @Pattern(regexp = "S|N")
    @Size(min = 1, max = 1)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ST_ALERTA_ATIVO", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String stAlertaAtivo = "S";

    public Trajeto() {
    }

    public Long getIdTrajeto() {
        return idTrajeto;
    }

    public void setIdTrajeto(Long idTrajeto) {
        this.idTrajeto = idTrajeto;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Estacao getEstacaoOrigem() {
        return estacaoOrigem;
    }

    public void setEstacaoOrigem(Estacao estacaoOrigem) {
        this.estacaoOrigem = estacaoOrigem;
    }

    public Estacao getEstacaoDestino() {
        return estacaoDestino;
    }

    public void setEstacaoDestino(Estacao estacaoDestino) {
        this.estacaoDestino = estacaoDestino;
    }

    public String getNmTrajeto() {
        return nmTrajeto;
    }

    public void setNmTrajeto(String nmTrajeto) {
        this.nmTrajeto = nmTrajeto;
    }

    public String getHrPartida() {
        return hrPartida;
    }

    public void setHrPartida(String hrPartida) {
        this.hrPartida = hrPartida;
    }

    public String getStAlertaAtivo() {
        return stAlertaAtivo;
    }

    public void setStAlertaAtivo(String stAlertaAtivo) {
        this.stAlertaAtivo = stAlertaAtivo;
    }

    @AssertTrue(message = "As estacoes de origem e destino devem ter IDs diferentes")
    public boolean isEstacoesDiferentes() {
        if (estacaoOrigem == null || estacaoDestino == null
                || estacaoOrigem.getIdEstacao() == null || estacaoDestino.getIdEstacao() == null) {
            return true; // A existencia das referencias sera verificada na BO.
        }
        return !estacaoOrigem.getIdEstacao().equals(estacaoDestino.getIdEstacao());
    }
}
