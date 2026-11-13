package br.com.fiap.embarqueCertoAPI.dto;

import java.time.LocalDateTime;

public class RegistroLotacaoRequest {

    private Long idRegistro;
    private Long idUsuario;
    private Long idLinha;
    private Long idEstacao;
    private Integer nrNivel;
    private String dsSentido;
    private LocalDateTime dtRegistro;

    public RegistroLotacaoRequest() {
    }

    public Long getIdRegistro() {
        return idRegistro;
    }

    public void setIdRegistro(Long idRegistro) {
        this.idRegistro = idRegistro;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
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
