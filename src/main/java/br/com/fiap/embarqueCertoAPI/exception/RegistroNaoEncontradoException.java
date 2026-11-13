package br.com.fiap.embarqueCertoAPI.exception;

public class RegistroNaoEncontradoException extends RuntimeException {

    public RegistroNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public RegistroNaoEncontradoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
