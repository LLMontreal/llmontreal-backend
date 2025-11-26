package br.com.montreal.ai.llmontreal.repository;

import br.com.montreal.ai.llmontreal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    boolean existsByEmail(String email);

    boolean existsByEmailAndDeletedAtIsNull(String email);
}
