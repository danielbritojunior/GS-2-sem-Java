package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.TipoOcorrencia;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TipoOcorrenciaDAO {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<TipoOcorrencia> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(TipoOcorrencia.class, id));
    }
}
