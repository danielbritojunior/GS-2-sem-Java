package br.com.fiap.embarqueCertoAPI.dto;

public class ErroResponse {

    private final int status;
    private final String mensagem;
    private final String caminho;

    public ErroResponse(int status, String mensagem, String caminho) {
        this.status = status;
        this.mensagem = mensagem;
        this.caminho = caminho;
    }

    public int getStatus() {
        return status;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getCaminho() {
        return caminho;
    }
}
