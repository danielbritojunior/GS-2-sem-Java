package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.LinhaEstacao;
import br.com.fiap.embarqueCertoAPI.model.id.LinhaEstacaoId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

@Repository
public class LinhaEstacaoDAO {

    @PersistenceContext
    private EntityManager entityManager;

    public boolean existePorId(LinhaEstacaoId id) {
        return entityManager.find(LinhaEstacao.class, id) != null;
    }
}
