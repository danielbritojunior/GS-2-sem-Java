package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.dao.EstacaoDAO;
import br.com.fiap.embarqueCertoAPI.exception.ConflitoException;
import br.com.fiap.embarqueCertoAPI.exception.RegistroNaoEncontradoException;
import br.com.fiap.embarqueCertoAPI.model.Estacao;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EstacaoBO {

    private final EstacaoDAO estacaoDAO;
    private final Validator validator;

    public EstacaoBO(EstacaoDAO estacaoDAO, Validator validator) {
        this.estacaoDAO = estacaoDAO;
        this.validator = validator;
    }

    public Estacao buscarPorId(Long id) {
        validarId(id);
        return estacaoDAO.buscarPorId(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Estacao nao encontrada: " + id));
    }

    public List<Estacao> listar() {
        return estacaoDAO.listar();
    }

    @Transactional
    public Estacao cadastrar(Estacao dados) {
        ValidacaoBO.entrada(dados);
        Estacao candidato = candidato(dados, null);
        ValidacaoBO.entidade(validator, candidato);
        if (estacaoDAO.buscarPorId(candidato.getIdEstacao()).isPresent()) {
            throw new ConflitoException("Ja existe Estacao com o ID informado");
        }
        verificarUnicidade(candidato, null);
        estacaoDAO.inserir(candidato);
        return candidato;
    }

    @Transactional
    public Estacao atualizar(Long id, Estacao dados) {
        ValidacaoBO.entrada(dados);
        ValidacaoBO.idAtualizacao(validator, Estacao.class, "idEstacao", id, dados.getIdEstacao());
        Estacao existente = buscarPorId(id);
        Estacao candidato = candidato(dados, existente);
        ValidacaoBO.entidade(validator, candidato);
        verificarUnicidade(candidato, id);
        copiarCampos(candidato, existente);
        estacaoDAO.atualizar(existente);
        return existente;
    }

    @Transactional
    public void excluir(Long id) {
        Estacao existente = buscarPorId(id);
        if (estacaoDAO.possuiDependencias(id)) {
            throw new ConflitoException("Estacao possui registros dependentes e nao pode ser excluida");
        }
        estacaoDAO.excluir(existente);
    }

    private void validarId(Long id) {
        ValidacaoBO.id(validator, Estacao.class, "idEstacao", id);
    }

    private Estacao candidato(Estacao dados, Estacao anterior) {
        Estacao candidato = new Estacao();
        candidato.setIdEstacao(anterior == null ? dados.getIdEstacao() : anterior.getIdEstacao());
        copiarCampos(dados, candidato);
        return candidato;
    }

    private void copiarCampos(Estacao origem, Estacao destino) {
        destino.setNmEstacao(origem.getNmEstacao());
        destino.setStAcessivel(origem.getStAcessivel());
        destino.setNrMinBaldeacao(origem.getNrMinBaldeacao());
        destino.setNrLatitude(origem.getNrLatitude());
        destino.setNrLongitude(origem.getNrLongitude());
    }

    private void verificarUnicidade(Estacao candidato, Long idIgnorado) {
        if (estacaoDAO.existeNome(candidato.getNmEstacao(), idIgnorado)) {
            throw new ConflitoException("Ja existe uma estacao com esse nome");
        }
    }
}
