package br.com.fiap.embarqueCertoAPI.bo;

import br.com.fiap.embarqueCertoAPI.dto.OcorrenciaResponse;
import br.com.fiap.embarqueCertoAPI.dao.EstacaoDAO;
import br.com.fiap.embarqueCertoAPI.dao.LinhaDAO;
import br.com.fiap.embarqueCertoAPI.dao.LinhaEstacaoDAO;
import br.com.fiap.embarqueCertoAPI.dao.OcorrenciaDAO;
import br.com.fiap.embarqueCertoAPI.dao.TipoOcorrenciaDAO;
import br.com.fiap.embarqueCertoAPI.dao.UsuarioDAO;
import br.com.fiap.embarqueCertoAPI.exception.ConflitoException;
import br.com.fiap.embarqueCertoAPI.exception.RegistroNaoEncontradoException;
import br.com.fiap.embarqueCertoAPI.exception.RegraNegocioException;
import br.com.fiap.embarqueCertoAPI.model.Estacao;
import br.com.fiap.embarqueCertoAPI.model.Linha;
import br.com.fiap.embarqueCertoAPI.model.Ocorrencia;
import br.com.fiap.embarqueCertoAPI.model.TipoOcorrencia;
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
public class OcorrenciaBO {

    private final OcorrenciaDAO ocorrenciaDAO;
    private final TipoOcorrenciaDAO tipoOcorrenciaDAO;
    private final LinhaDAO linhaDAO;
    private final EstacaoDAO estacaoDAO;
    private final UsuarioDAO usuarioDAO;
    private final LinhaEstacaoDAO linhaEstacaoDAO;
    private final Validator validator;
    private final Clock clock;

    public OcorrenciaBO(
            OcorrenciaDAO ocorrenciaDAO, TipoOcorrenciaDAO tipoOcorrenciaDAO, LinhaDAO linhaDAO,
            EstacaoDAO estacaoDAO, UsuarioDAO usuarioDAO, LinhaEstacaoDAO linhaEstacaoDAO,
            Validator validator, Clock clock) {
        this.ocorrenciaDAO = ocorrenciaDAO;
        this.tipoOcorrenciaDAO = tipoOcorrenciaDAO;
        this.linhaDAO = linhaDAO;
        this.estacaoDAO = estacaoDAO;
        this.usuarioDAO = usuarioDAO;
        this.linhaEstacaoDAO = linhaEstacaoDAO;
        this.validator = validator;
        this.clock = clock;
    }

    public Ocorrencia buscarPorId(Long id) {
        validarId(id);
        return ocorrenciaDAO.buscarPorId(id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Ocorrencia nao encontrada: " + id));
    }

    public List<Ocorrencia> listar() {
        return ocorrenciaDAO.listar();
    }

    @Transactional
    public Ocorrencia cadastrar(Ocorrencia dados) {
        ValidacaoBO.entrada(dados);
        Ocorrencia candidato = candidato(dados, null);
        ValidacaoBO.entidade(validator, candidato);
        if (ocorrenciaDAO.buscarPorId(candidato.getIdOcorrencia()).isPresent()) {
            throw new ConflitoException("Ja existe Ocorrencia com o ID informado");
        }
        resolverReferencias(candidato);
        ocorrenciaDAO.inserir(candidato);
        return candidato;
    }

    @Transactional
    public Ocorrencia atualizar(Long id, Ocorrencia dados) {
        ValidacaoBO.entrada(dados);
        ValidacaoBO.idAtualizacao(validator, Ocorrencia.class, "idOcorrencia", id, dados.getIdOcorrencia());
        Ocorrencia existente = buscarPorId(id);
        Ocorrencia candidato = candidato(dados, existente);
        ValidacaoBO.entidade(validator, candidato);
        resolverReferencias(candidato);
        copiarCampos(candidato, existente);
        ocorrenciaDAO.atualizar(existente);
        return existente;
    }

    @Transactional
    public void excluir(Long id) {
        Ocorrencia existente = buscarPorId(id);
        if (ocorrenciaDAO.possuiDependencias(id)) {
            throw new ConflitoException("Ocorrencia possui registros dependentes e nao pode ser excluida");
        }
        ocorrenciaDAO.excluir(existente);
    }

    @Transactional(readOnly = true)
    public List<OcorrenciaResponse> listarRespostas() {
        return listar().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OcorrenciaResponse buscarRespostaPorId(Long id) {
        return toResponse(buscarPorId(id));
    }

    @Transactional
    public OcorrenciaResponse cadastrarResposta(Ocorrencia dados) {
        return toResponse(cadastrar(dados));
    }

    @Transactional
    public OcorrenciaResponse atualizarResposta(Long id, Ocorrencia dados) {
        return toResponse(atualizar(id, dados));
    }

    private OcorrenciaResponse toResponse(Ocorrencia entidade) {
        return new OcorrenciaResponse(
                entidade.getIdOcorrencia(),
                entidade.getTipoOcorrencia().getIdTipoOcorrencia(),
                entidade.getLinha().getIdLinha(),
                entidade.getEstacao() == null ? null : entidade.getEstacao().getIdEstacao(),
                entidade.getUsuario() == null ? null : entidade.getUsuario().getIdUsuario(),
                entidade.getTpFonte(),
                entidade.getDsOcorrencia(),
                entidade.getNrAtrasoMin(),
                entidade.getStOcorrencia(),
                entidade.getDtInicio(),
                entidade.getDtFim());
    }

    private void validarId(Long id) {
        ValidacaoBO.id(validator, Ocorrencia.class, "idOcorrencia", id);
    }

    private Ocorrencia candidato(Ocorrencia dados, Ocorrencia anterior) {
        Ocorrencia candidato = new Ocorrencia();
        candidato.setIdOcorrencia(anterior == null ? dados.getIdOcorrencia() : anterior.getIdOcorrencia());
        copiarCampos(dados, candidato);
        candidato.setDtInicio(data(dados.getDtInicio(), anterior == null ? null : anterior.getDtInicio()));
        candidato.setDtFim(dados.getDtFim() == null ? null : dados.getDtFim().withNano(0));
        return candidato;
    }

    private void copiarCampos(Ocorrencia origem, Ocorrencia destino) {
        destino.setTipoOcorrencia(origem.getTipoOcorrencia());
        destino.setLinha(origem.getLinha());
        destino.setEstacao(origem.getEstacao());
        destino.setUsuario(origem.getUsuario());
        destino.setTpFonte(origem.getTpFonte());
        destino.setDsOcorrencia(origem.getDsOcorrencia());
        destino.setNrAtrasoMin(origem.getNrAtrasoMin());
        destino.setStOcorrencia(origem.getStOcorrencia());
        destino.setDtInicio(origem.getDtInicio());
        destino.setDtFim(origem.getDtFim());
    }

    private void resolverReferencias(Ocorrencia candidato) {
        Long idTipoOcorrencia = candidato.getTipoOcorrencia() == null ? null : candidato.getTipoOcorrencia().getIdTipoOcorrencia();
        ValidacaoBO.id(validator, TipoOcorrencia.class, "idTipoOcorrencia", idTipoOcorrencia);
        candidato.setTipoOcorrencia(tipoOcorrenciaDAO.buscarPorId(idTipoOcorrencia)
                .orElseThrow(() -> new RegraNegocioException("O tipo de ocorrencia informado nao existe")));
        Long idLinha = candidato.getLinha() == null ? null : candidato.getLinha().getIdLinha();
        ValidacaoBO.id(validator, Linha.class, "idLinha", idLinha);
        candidato.setLinha(linhaDAO.buscarPorId(idLinha)
                .orElseThrow(() -> new RegraNegocioException("A linha informada nao existe")));
        if (candidato.getEstacao() != null) {
            Long idEstacao = candidato.getEstacao().getIdEstacao();
            ValidacaoBO.id(validator, Estacao.class, "idEstacao", idEstacao);
            candidato.setEstacao(estacaoDAO.buscarPorId(idEstacao)
                    .orElseThrow(() -> new RegraNegocioException("A estacao informada nao existe")));
        }
        if (candidato.getUsuario() != null) {
            Long idUsuario = candidato.getUsuario().getIdUsuario();
            ValidacaoBO.id(validator, Usuario.class, "idUsuario", idUsuario);
            candidato.setUsuario(usuarioDAO.buscarPorId(idUsuario)
                    .orElseThrow(() -> new RegraNegocioException("O usuario informado nao existe")));
        }
        if (candidato.getEstacao() != null) {
            if (!linhaEstacaoDAO.existePorId(new LinhaEstacaoId(candidato.getLinha().getIdLinha(),
                    candidato.getEstacao().getIdEstacao()))) {
                throw new RegraNegocioException("A estacao informada nao pertence a linha");
            }
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
