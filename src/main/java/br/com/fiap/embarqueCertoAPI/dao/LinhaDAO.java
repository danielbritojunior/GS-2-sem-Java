package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Linha;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class LinhaDAO {

    static final String LISTAR_JPQL = "select e from Linha e order by e.idLinha";
    static final String UNICIDADE_JPQL = "select count(e) from Linha e where e.nrLinha = :numero"
            + " and (:idIgnorado is null or e.idLinha <> :idIgnorado)";
    static final String DEP_LINHAESTACAO_JPQL = "select count(d) from LinhaEstacao d where d.linha.idLinha = :id";
    static final String DEP_OCORRENCIA_JPQL = "select count(d) from Ocorrencia d where d.linha.idLinha = :id";
    static final String DEP_REGISTROLOTACAO_JPQL = "select count(d) from RegistroLotacao d where d.linha.idLinha = :id";

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Linha> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(Linha.class, id));
    }

    public List<Linha> listar() {
        return entityManager.createQuery(LISTAR_JPQL, Linha.class).getResultList();
    }

    public void inserir(Linha entidadeNova) {
        entityManager.persist(entidadeNova);
        entityManager.flush();
    }

    public void atualizar(Linha entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.flush();
    }

    public void excluir(Linha entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.remove(entidadeGerenciada);
        entityManager.flush();
    }

    private void exigirGerenciada(Linha entidade) {
        if (entidade == null || !entityManager.contains(entidade)) {
            throw new IllegalArgumentException("A operacao exige uma entidade gerenciada");
        }
    }

    public boolean existeNumero(Integer numero, Long idIgnorado) {
        return entityManager.createQuery(UNICIDADE_JPQL, Long.class)
                .setParameter("numero", numero)
                .setParameter("idIgnorado", idIgnorado)
                .getSingleResult() > 0;
    }

    public boolean possuiDependencias(Long id) {
        return contarDependencias(DEP_LINHAESTACAO_JPQL, id) > 0
                || contarDependencias(DEP_OCORRENCIA_JPQL, id) > 0
                || contarDependencias(DEP_REGISTROLOTACAO_JPQL, id) > 0;
    }

    private long contarDependencias(String jpql, Long id) {
        return entityManager.createQuery(jpql, Long.class).setParameter("id", id).getSingleResult();
    }
}
