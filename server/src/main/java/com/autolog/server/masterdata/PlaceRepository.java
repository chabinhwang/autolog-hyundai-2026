package com.autolog.server.masterdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	Optional<Place> findByIdAndOrgId(Long id, Long orgId);

	Optional<Place> findByIdAndOrgIdAndDeletedFalse(Long id, Long orgId);

	List<Place> findByOrgIdAndDeletedFalseOrderByIdAsc(Long orgId);

	List<Place> findByOrgIdAndTypeAndDeletedFalseOrderByIdAsc(Long orgId, PlaceType type);

	List<Place> findByOrgIdOrderByIdAsc(Long orgId);

	List<Place> findByOrgIdAndUpdatedAtGreaterThanEqualOrderByIdAsc(Long orgId, Instant since);
}
