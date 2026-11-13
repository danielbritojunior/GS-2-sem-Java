package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Estacao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class EstacaoDAO {

    static final String LISTAR_JPQL = "select e from Estacao e order by e.idEstacao";
    static final String UNICIDADE_JPQL = "select count(e) from Estacao e where e.nmEstacao = :nome"
            + " and (:idIgnorado is null or e.idEstacao <> :idIgnorado)";
    static final String DEP_LINHAESTACAO_JPQL = "select count(d) from LinhaEstacao d where d.estacao.idEstacao = :id";
    static final String DEP_OCORRENCIA_JPQL = "select count(d) from Ocorrencia d where d.estacao.idEstacao = :id";
    static final String DEP_REGISTROLOTACAO_JPQL = "select count(d) from RegistroLotacao d where d.estacao.idEstacao = :id";
    static final String DEP_TRAJETO_JPQL = "select count(d) from Trajeto d where d.estacaoOrigem.idEstacao = :id or d.estacaoDestino.idEstacao = :id";

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Estacao> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(Estacao.class, id));
    }

    public List<Estacao> listar() {
        return entityManager.createQuery(LISTAR_JPQL, Estacao.class).getResultList();
    }

    public void inserir(Estacao entidadeNova) {
        entityManager.persist(entidadeNova);
        entityManager.flush();
    }

    public void atualizar(Estacao entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.flush();
    }

    public void excluir(Estacao entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.remove(entidadeGerenciada);
        entityManager.flush();
    }

    private void exigirGerenciada(Estacao entidade) {
        if (entidade == null || !entityManager.contains(entidade)) {
            throw new IllegalArgumentException("A operacao exige uma entidade gerenciada");
        }
    }

    public boolean existeNome(String nome, Long idIgnorado) {
        return entityManager.createQuery(UNICIDADE_JPQL, Long.class)
                .setParameter("nome", nome)
                .setParameter("idIgnorado", idIgnorado)
                .getSingleResult() > 0;
    }

    public boolean possuiDependencias(Long id) {
        return contarDependencias(DEP_LINHAESTACAO_JPQL, id) > 0
                || contarDependencias(DEP_OCORRENCIA_JPQL, id) > 0
                || contarDependencias(DEP_REGISTROLOTACAO_JPQL, id) > 0
                || contarDependencias(DEP_TRAJETO_JPQL, id) > 0;
    }

    private long contarDependencias(String jpql, Long id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }
}
