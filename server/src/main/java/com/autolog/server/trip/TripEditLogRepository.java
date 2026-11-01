package com.autolog.server.trip;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripEditLogRepository extends JpaRepository<TripEditLog, Long> {

	List<TripEditLog> findByTripIdOrderBySeqAsc(Long tripId);

	Optional<TripEditLog> findByClientEditUuid(UUID clientEditUuid);

	@Query("select coalesce(max(e.seq), 0) from TripEditLog e where e.tripId = :tripId")
	int findMaxSeq(@Param("tripId") Long tripId);

	@Query("select distinct e.tripId from TripEditLog e where e.tripId in :tripIds")
	Set<Long> findEditedTripIds(@Param("tripIds") Collection<Long> tripIds);
}
