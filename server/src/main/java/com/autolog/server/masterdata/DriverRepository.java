package com.autolog.server.masterdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {

	Optional<Driver> findByIdAndOrgId(Long id, Long orgId);

	Optional<Driver> findByIdAndOrgIdAndActiveTrue(Long id, Long orgId);

	List<Driver> findByOrgIdOrderByIdAsc(Long orgId);

	List<Driver> findByOrgIdAndActiveTrueOrderByIdAsc(Long orgId);

	List<Driver> findByOrgIdAndUpdatedAtGreaterThanEqualOrderByIdAsc(Long orgId, Instant since);
}
