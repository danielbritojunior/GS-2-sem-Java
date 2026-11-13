package br.com.fiap.embarqueCertoAPI.dto;

import java.time.LocalDateTime;

public class OcorrenciaResponse {

    private final Long idOcorrencia;
    private final Long idTipoOcorrencia;
    private final Long idLinha;
    private final Long idEstacao;
    private final Long idUsuario;
    private final String tpFonte;
    private final String dsOcorrencia;
    private final Integer nrAtrasoMin;
    private final String stOcorrencia;
    private final LocalDateTime dtInicio;
    private final LocalDateTime dtFim;

    public OcorrenciaResponse(
            Long idOcorrencia, Long idTipoOcorrencia, Long idLinha, Long idEstacao, Long idUsuario,
            String tpFonte, String dsOcorrencia, Integer nrAtrasoMin, String stOcorrencia,
            LocalDateTime dtInicio, LocalDateTime dtFim) {
        this.idOcorrencia = idOcorrencia;
        this.idTipoOcorrencia = idTipoOcorrencia;
        this.idLinha = idLinha;
        this.idEstacao = idEstacao;
        this.idUsuario = idUsuario;
        this.tpFonte = tpFonte;
        this.dsOcorrencia = dsOcorrencia;
        this.nrAtrasoMin = nrAtrasoMin;
        this.stOcorrencia = stOcorrencia;
        this.dtInicio = dtInicio;
        this.dtFim = dtFim;
    }

    public Long getIdOcorrencia() {
        return idOcorrencia;
    }

    public Long getIdTipoOcorrencia() {
        return idTipoOcorrencia;
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public Long getIdEstacao() {
        return idEstacao;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public String getTpFonte() {
        return tpFonte;
    }

    public String getDsOcorrencia() {
        return dsOcorrencia;
    }

    public Integer getNrAtrasoMin() {
        return nrAtrasoMin;
    }

    public String getStOcorrencia() {
        return stOcorrencia;
    }

    public LocalDateTime getDtInicio() {
        return dtInicio;
    }

    public LocalDateTime getDtFim() {
        return dtFim;
    }
}
