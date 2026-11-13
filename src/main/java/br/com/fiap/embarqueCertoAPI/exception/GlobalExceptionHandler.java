package br.com.fiap.embarqueCertoAPI.exception;

import br.com.fiap.embarqueCertoAPI.dto.ErroResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<Integer, String> MENSAGENS_HTTP = Map.of(
            400, "JSON, parametro ou formato de dados invalido.",
            404, "Recurso nao encontrado.",
            405, "Metodo HTTP nao permitido.",
            406, "Formato de resposta nao suportado.",
            415, "Tipo de conteudo nao suportado.");

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException erro, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, erro.getMessage(), request);
    }

    @ExceptionHandler(RegistroNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RegistroNaoEncontradoException erro, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, erro.getMessage(), request);
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> conflito(ConflitoException erro, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, erro.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> integridade(DataIntegrityViolationException erro, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT,
                "Operacao nao permitida por restricoes de integridade dos dados.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> inesperada(Exception erro, HttpServletRequest request) {
        registrarFalha(erro);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "Falha interna ao processar a requisicao.", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception erro, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            registrarFalha(erro);
        }
        String mensagem = MENSAGENS_HTTP.getOrDefault(status.value(),
                status.is5xxServerError() ? "Falha interna ao processar a requisicao." : "Requisicao nao atendida.");
        String caminho = ((ServletWebRequest) request).getRequest().getRequestURI();
        return super.handleExceptionInternal(erro,
                new ErroResponse(status.value(), mensagem, caminho), headers, status, request);
    }

    private ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErroResponse(status.value(), mensagem, request.getRequestURI()));
    }

    private void registrarFalha(Exception erro) {
        // Nao copiar SQL, credenciais ou mensagens brutas de conexao para os logs.
        LOG.error("Falha interna no processamento HTTP. Tipo: {}", erro.getClass().getName());
    }
}
