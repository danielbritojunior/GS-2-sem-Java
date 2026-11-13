package br.com.fiap.embarqueCertoAPI.controller;

import br.com.fiap.embarqueCertoAPI.bo.OcorrenciaBO;
import br.com.fiap.embarqueCertoAPI.dto.OcorrenciaRequest;
import br.com.fiap.embarqueCertoAPI.dto.OcorrenciaResponse;
import br.com.fiap.embarqueCertoAPI.model.Estacao;
import br.com.fiap.embarqueCertoAPI.model.Linha;
import br.com.fiap.embarqueCertoAPI.model.Ocorrencia;
import br.com.fiap.embarqueCertoAPI.model.TipoOcorrencia;
import br.com.fiap.embarqueCertoAPI.model.Usuario;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocorrencias")
public class OcorrenciaController {

    private final OcorrenciaBO bo;

    public OcorrenciaController(OcorrenciaBO bo) {
        this.bo = bo;
    }

    @GetMapping
    public ResponseEntity<List<OcorrenciaResponse>> listar() {
        return ResponseEntity.ok(bo.listarRespostas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OcorrenciaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(bo.buscarRespostaPorId(id));
    }

    @PostMapping
    public ResponseEntity<OcorrenciaResponse> cadastrar(@RequestBody OcorrenciaRequest dados) {
        OcorrenciaResponse criado = bo.cadastrarResposta(toEntity(dados));
        URI location = URI.create("/api/ocorrencias/" + criado.getIdOcorrencia());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OcorrenciaResponse> atualizar(@PathVariable Long id, @RequestBody OcorrenciaRequest dados) {
        return ResponseEntity.ok(bo.atualizarResposta(id, toEntity(dados)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        bo.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private Ocorrencia toEntity(OcorrenciaRequest dados) {
        Ocorrencia entidade = new Ocorrencia();
        entidade.setIdOcorrencia(dados.getIdOcorrencia());
        if (dados.getIdTipoOcorrencia() != null) {
            TipoOcorrencia referencia = new TipoOcorrencia();
            referencia.setIdTipoOcorrencia(dados.getIdTipoOcorrencia());
            entidade.setTipoOcorrencia(referencia);
        }
        if (dados.getIdLinha() != null) {
            Linha referencia = new Linha();
            referencia.setIdLinha(dados.getIdLinha());
            entidade.setLinha(referencia);
        }
        if (dados.getIdEstacao() != null) {
            Estacao referencia = new Estacao();
            referencia.setIdEstacao(dados.getIdEstacao());
            entidade.setEstacao(referencia);
        }
        if (dados.getIdUsuario() != null) {
            Usuario referencia = new Usuario();
            referencia.setIdUsuario(dados.getIdUsuario());
            entidade.setUsuario(referencia);
        }
        entidade.setTpFonte(dados.getTpFonte());
        entidade.setDsOcorrencia(dados.getDsOcorrencia());
        entidade.setNrAtrasoMin(dados.getNrAtrasoMin());
        entidade.setStOcorrencia(dados.getStOcorrencia());
        entidade.setDtInicio(dados.getDtInicio());
        entidade.setDtFim(dados.getDtFim());
        return entidade;
    }
}
