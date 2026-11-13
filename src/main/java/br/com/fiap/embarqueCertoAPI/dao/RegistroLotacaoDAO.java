package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.RegistroLotacao;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class RegistroLotacaoDAO {

    static final String LISTAR_JPQL = "select e from RegistroLotacao e order by e.idRegistro";

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<RegistroLotacao> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(RegistroLotacao.class, id));
    }

    public List<RegistroLotacao> listar() {
        return entityManager.createQuery(LISTAR_JPQL, RegistroLotacao.class).getResultList();
    }

    public void inserir(RegistroLotacao entidadeNova) {
        entityManager.persist(entidadeNova);
        entityManager.flush();
    }

    public void atualizar(RegistroLotacao entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.flush();
    }

    public void excluir(RegistroLotacao entidadeGerenciada) {
        exigirGerenciada(entidadeGerenciada);
        entityManager.remove(entidadeGerenciada);
        entityManager.flush();
    }

    private void exigirGerenciada(RegistroLotacao entidade) {
        if (entidade == null || !entityManager.contains(entidade)) {
            throw new IllegalArgumentException("A operacao exige uma entidade gerenciada");
        }
    }
}
