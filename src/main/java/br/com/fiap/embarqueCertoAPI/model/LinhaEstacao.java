package br.com.fiap.embarqueCertoAPI.model;

import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "T_EC_LINHA_ESTACAO", uniqueConstraints = {
        @UniqueConstraint(name = "UN_EC_LINHA_ESTACAO_ORDEM", columnNames = {"ID_LINHA", "NR_ORDEM"})
})
public class LinhaEstacao {

    @NotNull
    @Valid
    @EmbeddedId
    private LinhaEstacaoId id;

    @NotNull
    @MapsId("idLinha")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_LINHA", referencedColumnName = "ID_LINHA", nullable = false,
            columnDefinition = "NUMBER(3)", foreignKey = @ForeignKey(name = "FK_EC_LINHA_ESTACAO_LINHA"))
    private Linha linha;

    @NotNull
    @MapsId("idEstacao")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_ESTACAO", referencedColumnName = "ID_ESTACAO", nullable = false,
            columnDefinition = "NUMBER(5)", foreignKey = @ForeignKey(name = "FK_EC_LINHA_ESTACAO_ESTACAO"))
    private Estacao estacao;

    @NotNull
    @Min(1)
    @Max(999)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_ORDEM", nullable = false, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Integer nrOrdem;

    @Min(1)
    @Max(999)
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "NR_MIN_PROXIMA", nullable = true, precision = 3, scale = 0, columnDefinition = "NUMBER(3)")
    private Integer nrMinProxima;

    public LinhaEstacao() {
    }

    public LinhaEstacaoId getId() {
        return id;
    }

    public void setId(LinhaEstacaoId id) {
        this.id = id;
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

    public Integer getNrOrdem() {
        return nrOrdem;
    }

    public void setNrOrdem(Integer nrOrdem) {
        this.nrOrdem = nrOrdem;
    }

    public Integer getNrMinProxima() {
        return nrMinProxima;
    }

    public void setNrMinProxima(Integer nrMinProxima) {
        this.nrMinProxima = nrMinProxima;
    }
}
