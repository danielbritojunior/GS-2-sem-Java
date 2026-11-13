package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.dto.RegistroLotacaoResponse;
import br.com.fiap.embarqueCertoAPI.dao.EstacaoDAO;
import br.com.fiap.embarqueCertoAPI.dao.LinhaDAO;
import br.com.fiap.embarqueCertoAPI.dao.LinhaEstacaoDAO;
import br.com.fiap.embarqueCertoAPI.dao.RegistroLotacaoDAO;
import br.com.fiap.embarqueCertoAPI.dao.UsuarioDAO;
import br.com.fiap.embarqueCertoAPI.exception.ConflitoException;
import br.com.fiap.embarqueCertoAPI.exception.RegistroNaoEncontradoException;
import br.com.fiap.embarqueCertoAPI.exception.RegraNegocioException;
import br.com.fiap.embarqueCertoAPI.model.Estacao;
import br.com.fiap.embarqueCertoAPI.model.Linha;
import br.com.fiap.embarqueCertoAPI.model.RegistroLotacao;
import br.com.fiap.embarqueCertoAPI.model.Usuario;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RegistroLotacaoBO {

    private final RegistroLotacaoDAO registroLotacaoDAO;
    private final UsuarioDAO usuarioDAO;
    private final LinhaDAO linhaDAO;
    private final EstacaoDAO estacaoDAO;
    private final LinhaEstacaoDAO linhaEstacaoDAO;
    private final Validator validator;
    private final Clock clock;

    public RegistroLotacaoBO(
            RegistroLotacaoDAO registroLotacaoDAO, UsuarioDAO usuarioDAO, LinhaDAO linhaDAO,
            EstacaoDAO estacaoDAO, LinhaEstacaoDAO linhaEstacaoDAO, Validator validator, Clock clock) {
        this.registroLotacaoDAO = registroLotacaoDAO;
        this.usuarioDAO = usuarioDAO;
        this.linhaDAO = linhaDAO;
        this.estacaoDAO = estacaoDAO;
        this.linhaEstacaoDAO = linhaEstacaoDAO;
        this.validator = validator;
        this.clock = clock;
    }

    public RegistroLotacao buscarPorId(Long id) {
        validarId(id);
        return registroLotacaoDAO.buscarPorId(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Registro de lotacao nao encontrado: " + id));
    }

    public List<RegistroLotacao> listar() {
        return registroLotacaoDAO.listar();
    }

    @Transactional
    public RegistroLotacao cadastrar(RegistroLotacao dados) {
        ValidacaoBO.entrada(dados);
        RegistroLotacao candidato = candidato(dados, null);
        ValidacaoBO.entidade(validator, candidato);
        if (registroLotacaoDAO.buscarPorId(candidato.getIdRegistro()).isPresent()) {
            throw new ConflitoException("Ja existe RegistroLotacao com o ID informado");
        }
        resolverReferencias(candidato);
        registroLotacaoDAO.inserir(candidato);
        return candidato;
    }

    @Transactional
    public RegistroLotacao atualizar(Long id, RegistroLotacao dados) {
        ValidacaoBO.entrada(dados);
        ValidacaoBO.idAtualizacao(validator, RegistroLotacao.class, "idRegistro", id, dados.getIdRegistro());
        RegistroLotacao existente = buscarPorId(id);
        RegistroLotacao candidato = candidato(dados, existente);
        ValidacaoBO.entidade(validator, candidato);
        resolverReferencias(candidato);
        copiarCampos(candidato, existente);
        registroLotacaoDAO.atualizar(existente);
        return existente;
    }

    @Transactional
    public void excluir(Long id) {
        RegistroLotacao existente = buscarPorId(id);
        registroLotacaoDAO.excluir(existente);
    }

    @Transactional(readOnly = true)
    public List<RegistroLotacaoResponse> listarRespostas() {
        return listar().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RegistroLotacaoResponse buscarRespostaPorId(Long id) {
        return toResponse(buscarPorId(id));
    }

    @Transactional
    public RegistroLotacaoResponse cadastrarResposta(RegistroLotacao dados) {
        return toResponse(cadastrar(dados));
    }

    @Transactional
    public RegistroLotacaoResponse atualizarResposta(Long id, RegistroLotacao dados) {
        return toResponse(atualizar(id, dados));
    }

    private RegistroLotacaoResponse toResponse(RegistroLotacao entidade) {
        return new RegistroLotacaoResponse(
                entidade.getIdRegistro(),
                entidade.getUsuario().getIdUsuario(),
                entidade.getLinha().getIdLinha(),
                entidade.getEstacao().getIdEstacao(),
                entidade.getNrNivel(),
                entidade.getDsSentido(),
                entidade.getDtRegistro());
    }

    private void validarId(Long id) {
        ValidacaoBO.id(validator, RegistroLotacao.class, "idRegistro", id);
    }

    private RegistroLotacao candidato(RegistroLotacao dados, RegistroLotacao anterior) {
        RegistroLotacao candidato = new RegistroLotacao();
        candidato.setIdRegistro(anterior == null ? dados.getIdRegistro() : anterior.getIdRegistro());
        copiarCampos(dados, candidato);
        candidato.setDtRegistro(data(dados.getDtRegistro(), anterior == null ? null : anterior.getDtRegistro()));
        return candidato;
    }

    private void copiarCampos(RegistroLotacao origem, RegistroLotacao destino) {
        destino.setUsuario(origem.getUsuario());
        destino.setLinha(origem.getLinha());
        destino.setEstacao(origem.getEstacao());
        destino.setNrNivel(origem.getNrNivel());
        destino.setDsSentido(origem.getDsSentido());
        destino.setDtRegistro(origem.getDtRegistro());
    }

    private void resolverReferencias(RegistroLotacao candidato) {
        Long idUsuario = candidato.getUsuario() == null ? null : candidato.getUsuario().getIdUsuario();
        ValidacaoBO.id(validator, Usuario.class, "idUsuario", idUsuario);
        candidato.setUsuario(usuarioDAO.buscarPorId(idUsuario)
                .orElseThrow(() -> new RegraNegocioException("O usuario informado nao existe")));
        Long idLinha = candidato.getLinha() == null ? null : candidato.getLinha().getIdLinha();
        ValidacaoBO.id(validator, Linha.class, "idLinha", idLinha);
        candidato.setLinha(linhaDAO.buscarPorId(idLinha)
                .orElseThrow(() -> new RegraNegocioException("A linha informada nao existe")));
        Long idEstacao = candidato.getEstacao() == null ? null : candidato.getEstacao().getIdEstacao();
        ValidacaoBO.id(validator, Estacao.class, "idEstacao", idEstacao);
        candidato.setEstacao(estacaoDAO.buscarPorId(idEstacao)
                .orElseThrow(() -> new RegraNegocioException("A estacao informada nao existe")));
        if (!linhaEstacaoDAO.existePorId(new LinhaEstacaoId(candidato.getLinha().getIdLinha(),
                candidato.getEstacao().getIdEstacao()))) {
            throw new RegraNegocioException("A estacao informada nao pertence a linha");
        }
    }

    private LocalDateTime data(LocalDateTime informada, LocalDateTime anterior) {
        if (informada != null) {
            return informada.withNano(0);
        }
        if (anterior != null) {
            return anterior.withNano(0);
        }
        return LocalDateTime.now(clock).withNano(0);
    }
}
