package br.com.fiap.embarqueCertoAPI.dto;

import java.time.LocalDateTime;

public class RegistroLotacaoResponse {

    private final Long idRegistro;
    private final Long idUsuario;
    private final Long idLinha;
    private final Long idEstacao;
    private final Integer nrNivel;
    private final String dsSentido;
    private final LocalDateTime dtRegistro;

    public RegistroLotacaoResponse(
            Long idRegistro, Long idUsuario, Long idLinha, Long idEstacao, Integer nrNivel,
            String dsSentido, LocalDateTime dtRegistro) {
        this.idRegistro = idRegistro;
        this.idUsuario = idUsuario;
        this.idLinha = idLinha;
        this.idEstacao = idEstacao;
        this.nrNivel = nrNivel;
        this.dsSentido = dsSentido;
        this.dtRegistro = dtRegistro;
    }

    public Long getIdRegistro() {
        return idRegistro;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public Long getIdEstacao() {
        return idEstacao;
    }

    public Integer getNrNivel() {
        return nrNivel;
    }

    public String getDsSentido() {
        return dsSentido;
    }

    public LocalDateTime getDtRegistro() {
        return dtRegistro;
    }
}
