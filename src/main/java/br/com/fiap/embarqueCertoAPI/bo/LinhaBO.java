package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.dto.LinhaResponse;
import br.com.fiap.embarqueCertoAPI.dao.LinhaDAO;
import br.com.fiap.embarqueCertoAPI.dao.OperadoraDAO;
import br.com.fiap.embarqueCertoAPI.exception.ConflitoException;
import br.com.fiap.embarqueCertoAPI.exception.RegistroNaoEncontradoException;
import br.com.fiap.embarqueCertoAPI.exception.RegraNegocioException;
import br.com.fiap.embarqueCertoAPI.model.Linha;
import br.com.fiap.embarqueCertoAPI.model.Operadora;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LinhaBO {

    private final LinhaDAO linhaDAO;
    private final OperadoraDAO operadoraDAO;
    private final Validator validator;

    public LinhaBO(LinhaDAO linhaDAO, OperadoraDAO operadoraDAO, Validator validator) {
        this.linhaDAO = linhaDAO;
        this.operadoraDAO = operadoraDAO;
        this.validator = validator;
    }

    public Linha buscarPorId(Long id) {
        validarId(id);
        return linhaDAO.buscarPorId(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Linha nao encontrada: " + id));
    }

    public List<Linha> listar() {
        return linhaDAO.listar();
    }

    @Transactional
    public Linha cadastrar(Linha dados) {
        ValidacaoBO.entrada(dados);
        Linha candidato = candidato(dados, null);
        ValidacaoBO.entidade(validator, candidato);
        if (linhaDAO.buscarPorId(candidato.getIdLinha()).isPresent()) {
            throw new ConflitoException("Ja existe Linha com o ID informado");
        }
        resolverReferencias(candidato);
        verificarUnicidade(candidato, null);
        linhaDAO.inserir(candidato);
        return candidato;
    }

    @Transactional
    public Linha atualizar(Long id, Linha dados) {
        ValidacaoBO.entrada(dados);
        ValidacaoBO.idAtualizacao(validator, Linha.class, "idLinha", id, dados.getIdLinha());
        Linha existente = buscarPorId(id);
        Linha candidato = candidato(dados, existente);
        ValidacaoBO.entidade(validator, candidato);
        resolverReferencias(candidato);
        verificarUnicidade(candidato, id);
        copiarCampos(candidato, existente);
        linhaDAO.atualizar(existente);
        return existente;
    }

    @Transactional
    public void excluir(Long id) {
        Linha existente = buscarPorId(id);
        if (linhaDAO.possuiDependencias(id)) {
            throw new ConflitoException("Linha possui registros dependentes e nao pode ser excluida");
        }
        linhaDAO.excluir(existente);
    }

    @Transactional(readOnly = true)
    public List<LinhaResponse> listarRespostas() {
        return listar().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public LinhaResponse buscarRespostaPorId(Long id) {
        return toResponse(buscarPorId(id));
    }

    @Transactional
    public LinhaResponse cadastrarResposta(Linha dados) {
        return toResponse(cadastrar(dados));
    }

    @Transactional
    public LinhaResponse atualizarResposta(Long id, Linha dados) {
        return toResponse(atualizar(id, dados));
    }

    private LinhaResponse toResponse(Linha entidade) {
        return new LinhaResponse(
                entidade.getIdLinha(),
                entidade.getOperadora().getIdOperadora(),
                entidade.getNrLinha(),
                entidade.getNmLinha(),
                entidade.getTpModal(),
                entidade.getDsCorHex());
    }

    private void validarId(Long id) {
        ValidacaoBO.id(validator, Linha.class, "idLinha", id);
    }

    private Linha candidato(Linha dados, Linha anterior) {
        Linha candidato = new Linha();
        candidato.setIdLinha(anterior == null ? dados.getIdLinha() : anterior.getIdLinha());
        copiarCampos(dados, candidato);
        return candidato;
    }

    private void copiarCampos(Linha origem, Linha destino) {
        destino.setOperadora(origem.getOperadora());
        destino.setNrLinha(origem.getNrLinha());
        destino.setNmLinha(origem.getNmLinha());
        destino.setTpModal(origem.getTpModal());
        destino.setDsCorHex(origem.getDsCorHex());
    }

    private void verificarUnicidade(Linha candidato, Long idIgnorado) {
        if (linhaDAO.existeNumero(candidato.getNrLinha(), idIgnorado)) {
            throw new ConflitoException("Ja existe uma linha com esse numero");
        }
    }

    private void resolverReferencias(Linha candidato) {
        Long idOperadora = candidato.getOperadora() == null ? null : candidato.getOperadora().getIdOperadora();
        ValidacaoBO.id(validator, Operadora.class, "idOperadora", idOperadora);
        candidato.setOperadora(operadoraDAO.buscarPorId(idOperadora)
                .orElseThrow(() -> new RegraNegocioException("A operadora informada nao existe")));
    }
}
