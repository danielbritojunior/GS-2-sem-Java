package br.com.fiap.embarqueCertoAPI.dto;

import java.time.LocalDateTime;

public class OcorrenciaRequest {

    private Long idOcorrencia;
    private Long idTipoOcorrencia;
    private Long idLinha;
    private Long idEstacao;
    private Long idUsuario;
    private String tpFonte;
    private String dsOcorrencia;
    private Integer nrAtrasoMin = 0;
    private String stOcorrencia = "ABERTA";
    private LocalDateTime dtInicio;
    private LocalDateTime dtFim;

    public OcorrenciaRequest() {
    }

    public Long getIdOcorrencia() {
        return idOcorrencia;
    }

    public void setIdOcorrencia(Long idOcorrencia) {
        this.idOcorrencia = idOcorrencia;
    }

    public Long getIdTipoOcorrencia() {
        return idTipoOcorrencia;
    }

    public void setIdTipoOcorrencia(Long idTipoOcorrencia) {
        this.idTipoOcorrencia = idTipoOcorrencia;
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public void setIdLinha(Long idLinha) {
        this.idLinha = idLinha;
    }

    public Long getIdEstacao() {
        return idEstacao;
    }

    public void setIdEstacao(Long idEstacao) {
        this.idEstacao = idEstacao;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getTpFonte() {
        return tpFonte;
    }

    public void setTpFonte(String tpFonte) {
        this.tpFonte = tpFonte;
    }

    public String getDsOcorrencia() {
        return dsOcorrencia;
    }

    public void setDsOcorrencia(String dsOcorrencia) {
        this.dsOcorrencia = dsOcorrencia;
    }

    public Integer getNrAtrasoMin() {
        return nrAtrasoMin;
    }

    public void setNrAtrasoMin(Integer nrAtrasoMin) {
        this.nrAtrasoMin = nrAtrasoMin;
    }

    public String getStOcorrencia() {
        return stOcorrencia;
    }

    public void setStOcorrencia(String stOcorrencia) {
        this.stOcorrencia = stOcorrencia;
    }

    public LocalDateTime getDtInicio() {
        return dtInicio;
    }

    public void setDtInicio(LocalDateTime dtInicio) {
        this.dtInicio = dtInicio;
    }

    public LocalDateTime getDtFim() {
        return dtFim;
    }

    public void setDtFim(LocalDateTime dtFim) {
        this.dtFim = dtFim;
    }
}
