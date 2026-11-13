package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Operadora;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class OperadoraDAO {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Operadora> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(Operadora.class, id));
    }
}
