package br.com.fiap.embarqueCertoAPI.dto;

public class LinhaRequest {

    private Long idLinha;
    private Long idOperadora;
    private Integer nrLinha;
    private String nmLinha;
    private String tpModal;
    private String dsCorHex;

    public LinhaRequest() {
    }

    public Long getIdLinha() {
        return idLinha;
    }

    public void setIdLinha(Long idLinha) {
        this.idLinha = idLinha;
    }

    public Long getIdOperadora() {
        return idOperadora;
    }

    public void setIdOperadora(Long idOperadora) {
        this.idOperadora = idOperadora;
    }

    public Integer getNrLinha() {
        return nrLinha;
    }

    public void setNrLinha(Integer nrLinha) {
        this.nrLinha = nrLinha;
    }

    public String getNmLinha() {
        return nmLinha;
    }

    public void setNmLinha(String nmLinha) {
        this.nmLinha = nmLinha;
    }

    public String getTpModal() {
        return tpModal;
    }

    public void setTpModal(String tpModal) {
        this.tpModal = tpModal;
    }

    public String getDsCorHex() {
        return dsCorHex;
    }

    public void setDsCorHex(String dsCorHex) {
        this.dsCorHex = dsCorHex;
    }
}
