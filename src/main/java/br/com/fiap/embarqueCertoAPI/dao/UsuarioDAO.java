package br.com.fiap.embarqueCertoAPI.dao;

import br.com.fiap.embarqueCertoAPI.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioDAO {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<Usuario> buscarPorId(Long id) {
        return Optional.ofNullable(entityManager.find(Usuario.class, id));
    }
}
