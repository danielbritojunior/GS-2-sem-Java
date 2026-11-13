package br.com.fiap.embarqueCertoAPI.exception;

public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }

    public ConflitoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
