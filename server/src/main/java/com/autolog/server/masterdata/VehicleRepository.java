package com.autolog.server.masterdata;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

	@EntityGraph(attributePaths = "defaultDriver")
	Optional<Vehicle> findByIdAndOrgId(Long id, Long orgId);

	@EntityGraph(attributePaths = "defaultDriver")
	Optional<Vehicle> findByIdAndOrgIdAndActiveTrue(Long id, Long orgId);

	@EntityGraph(attributePaths = "defaultDriver")
	List<Vehicle> findByOrgIdOrderByIdAsc(Long orgId);

	@EntityGraph(attributePaths = "defaultDriver")
	List<Vehicle> findByOrgIdAndActiveTrueOrderByIdAsc(Long orgId);

	boolean existsByOrgIdAndRegNo(Long orgId, String regNo);

	boolean existsByOrgIdAndRegNoAndIdNot(Long orgId, String regNo, Long id);

	List<Vehicle> findByDefaultDriver(Driver driver);
}
