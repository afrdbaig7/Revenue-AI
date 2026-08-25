package com.recoverai.auth.infrastructure;

import com.recoverai.auth.domain.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  java.util.Optional<User> findByEmail(String email);
}
