package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Ocorrencia;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OcorrenciaDAO {

    static final String LISTAR_JPQL = "select e from Ocorrencia e order by e.idOcorrencia";
    static final String DEP_CONFIRMACAO_JPQL = "select count(d) from Confirmacao d where d.ocorrencia.idOcorrencia = :id";
    static final String DEP_ALERTA_JPQL = "select count(d) from Alerta d where d.ocorrencia.idOcorrencia = :id";

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Ocorrencia> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(Ocorrencia.class, id));
    }

    public List<Ocorrencia> listar() {
        return entityManager.createQuery(LISTAR_JPQL, Ocorrencia.class).getResultList();
    }

    public void inserir(Ocorrencia entidadeNova) {
        entityManager.persist(entidadeNova);
        entityManager.flush();
    }

    public void atualizar(Ocorrencia entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.flush();
    }

    public void excluir(Ocorrencia entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.remove(entidadeGerenciada);
        entityManager.flush();
    }

    private void exigirGerenciada(Ocorrencia entidade) {
        if (entidade == null || !entityManager.contains(entidade)) {
            throw new IllegalArgumentException("A operacao exige uma entidade gerenciada");
        }
    }

    public boolean possuiDependencias(Long id) {
        return contarDependencias(DEP_CONFIRMACAO_JPQL, id) > 0
                || contarDependencias(DEP_ALERTA_JPQL, id) > 0;
    }

    private long contarDependencias(String jpql, Long id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }
}
