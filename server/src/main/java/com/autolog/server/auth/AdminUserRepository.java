package com.autolog.server.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

	Optional<AdminUser> findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);
}
