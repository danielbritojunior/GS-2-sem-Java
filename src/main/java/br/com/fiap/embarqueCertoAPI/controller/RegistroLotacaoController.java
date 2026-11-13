package br.com.fiap.embarqueCertoAPI.controller;

import br.com.fiap.embarqueCertoAPI.bo.RegistroLotacaoBO;
import br.com.fiap.embarqueCertoAPI.dto.RegistroLotacaoRequest;
import br.com.fiap.embarqueCertoAPI.dto.RegistroLotacaoResponse;
import br.com.fiap.embarqueCertoAPI.model.Estacao;
import br.com.fiap.embarqueCertoAPI.model.Linha;
import br.com.fiap.embarqueCertoAPI.model.RegistroLotacao;
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
@RequestMapping("/api/registros-lotacao")
public class RegistroLotacaoController {

    private final RegistroLotacaoBO bo;

    public RegistroLotacaoController(RegistroLotacaoBO bo) {
        this.bo = bo;
    }

    @GetMapping
    public ResponseEntity<List<RegistroLotacaoResponse>> listar() {
        return ResponseEntity.ok(bo.listarRespostas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RegistroLotacaoResponse> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(bo.buscarRespostaPorId(id));
    }

    @PostMapping
    public ResponseEntity<RegistroLotacaoResponse> cadastrar(@RequestBody RegistroLotacaoRequest dados) {
        RegistroLotacaoResponse criado = bo.cadastrarResposta(toEntity(dados));
        URI location = URI.create("/api/registros-lotacao/" + criado.getIdRegistro());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegistroLotacaoResponse> atualizar(@PathVariable Long id, @RequestBody RegistroLotacaoRequest dados) {
        return ResponseEntity.ok(bo.atualizarResposta(id, toEntity(dados)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        bo.excluir(id);
        return ResponseEntity.noContent().build();
    }

    private RegistroLotacao toEntity(RegistroLotacaoRequest dados) {
        RegistroLotacao entidade = new RegistroLotacao();
        entidade.setIdRegistro(dados.getIdRegistro());
        if (dados.getIdUsuario() != null) {
            Usuario referencia = new Usuario();
            referencia.setIdUsuario(dados.getIdUsuario());
            entidade.setUsuario(referencia);
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
        entidade.setNrNivel(dados.getNrNivel());
        entidade.setDsSentido(dados.getDsSentido());
        entidade.setDtRegistro(dados.getDtRegistro());
        return entidade;
    }
}
