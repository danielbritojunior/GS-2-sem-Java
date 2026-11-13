package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.exception.RegraNegocioException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;

/** Reutiliza as constraints do Model sem validar recursivamente as referencias. */
final class ValidacaoBO {

    private ValidacaoBO() {
    }

    static void entrada(Object dados) {
        if (dados == null) {
            throw new RegraNegocioException("Os dados devem ser informados");
        }
    }

    static void id(Validator validator, Class<?> tipo, String atributo, Long id) {
        verificar(validator.validateValue(tipo, atributo, id));
    }

    static void idAtualizacao(Validator validator, Class<?> tipo, String atributo, Long id, Long informado) {
        id(validator, tipo, atributo, id);
        if (informado != null && !informado.equals(id)) {
            throw new RegraNegocioException("O ID informado diverge do registro que sera atualizado");
        }
    }

    static void entidade(Validator validator, Object entidade) {
        verificar(validator.validate(entidade));
    }

    private static void verificar(Set<? extends ConstraintViolation<?>> violacoes) {
        if (!violacoes.isEmpty()) {
            String mensagem = violacoes.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted().collect(Collectors.joining("; "));
            throw new RegraNegocioException(mensagem);
        }
    }
}
