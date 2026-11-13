package br.com.fiap.embarqueCertoAPI.dto;

public class LinhaResponse {

    private final Long idLinha;
    private final Long idOperadora;
    private final Integer nrLinha;
    private final String nmLinha;
    private final String tpModal;
    private final String dsCorHex;

    public LinhaResponse(
            Long idLinha, Long idOperadora, Integer nrLinha, String nmLinha, String tpModal,
            String dsCorHex) {
        this.idLinha = idLinha;
        this.idOperadora = idOperadora;
        this.nrLinha = nrLinha;
        this.nmLinha = nmLinha;
        this.tpModal = tpModal;
        this.dsCorHex = dsCorHex;
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public Long getIdOperadora() {
        return idOperadora;
    }

    public Integer getNrLinha() {
        return nrLinha;
    }

    public String getNmLinha() {
        return nmLinha;
    }

    public String getTpModal() {
        return tpModal;
    }

    public String getDsCorHex() {
        return dsCorHex;
    }
}
