package com.cloudstorage.repository;

import com.cloudstorage.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    List<User> findByDeletedFalseOrderByCreatedAtAsc();

    boolean existsByUsername(String username);
}
