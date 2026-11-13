package br.com.fiap.embarqueCertoAPI.controller;

import br.com.fiap.embarqueCertoAPI.bo.LinhaBO;
import br.com.fiap.embarqueCertoAPI.dto.LinhaRequest;
import br.com.fiap.embarqueCertoAPI.dto.LinhaResponse;
import br.com.fiap.embarqueCertoAPI.model.Linha;
import br.com.fiap.embarqueCertoAPI.model.Operadora;
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
@RequestMapping("/api/linhas")
public class LinhaController {

    private final LinhaBO bo;

    public LinhaController(LinhaBO bo) {
        this.bo = bo;
    }

    @GetMapping
    public ResponseEntity<List<LinhaResponse>> listar() {
        return ResponseEntity.ok(bo.listarRespostas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LinhaResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(bo.buscarRespostaPorId(id));
    }

    @PostMapping
    public ResponseEntity<LinhaResponse> cadastrar(@RequestBody LinhaRequest dados) {
        LinhaResponse criado = bo.cadastrarResposta(toEntity(dados));
        URI location = URI.create("/api/linhas/" + criado.getIdLinha());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LinhaResponse> atualizar(@PathVariable Long id, @RequestBody LinhaRequest dados) {
        return ResponseEntity.ok(bo.atualizarResposta(id, toEntity(dados)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        bo.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private Linha toEntity(LinhaRequest dados) {
        Linha entidade = new Linha();
        entidade.setIdLinha(dados.getIdLinha());
        if (dados.getIdOperadora() != null) {
            Operadora referencia = new Operadora();
            referencia.setIdOperadora(dados.getIdOperadora());
            entidade.setOperadora(referencia);
        }
        entidade.setNrLinha(dados.getNrLinha());
        entidade.setNmLinha(dados.getNmLinha());
        entidade.setTpModal(dados.getTpModal());
        entidade.setDsCorHex(dados.getDsCorHex());
        return entidade;
    }
}
