package br.com.fiap.embarqueCertoAPI.controller;

import br.com.fiap.embarqueCertoAPI.bo.EstacaoBO;
import br.com.fiap.embarqueCertoAPI.model.Estacao;
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
@RequestMapping("/api/estacoes")
public class EstacaoController {

    private final EstacaoBO bo;

    public EstacaoController(EstacaoBO bo) {
        this.bo = bo;
    }

    @GetMapping
    public ResponseEntity<List<Estacao>> listar() {
        return ResponseEntity.ok(bo.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Estacao> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(bo.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Estacao> cadastrar(@RequestBody Estacao dados) {
        Estacao criado = bo.cadastrar(dados);
        URI location = URI.create("/api/estacoes/" + criado.getIdEstacao());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Estacao> atualizar(@PathVariable Long id, @RequestBody Estacao dados) {
        return ResponseEntity.ok(bo.atualizar(id, dados));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        bo.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
