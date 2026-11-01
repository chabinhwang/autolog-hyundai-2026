package com.autolog.server.trip;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip> {

	@Query("select t.clientUuid from Trip t where t.clientUuid in :uuids")
	Set<UUID> findExistingClientUuids(@Param("uuids") Collection<UUID> uuids);

	@EntityGraph(attributePaths = {"vehicle", "driver"})
	Optional<Trip> findByClientUuidAndOrgId(UUID clientUuid, Long orgId);

	/** 수정 시 행 잠금: 같은 트립의 수정 순번(seq)이 겹치지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from Trip t where t.clientUuid = :clientUuid and t.orgId = :orgId")
	Optional<Trip> findForUpdate(@Param("clientUuid") UUID clientUuid, @Param("orgId") Long orgId);

	@EntityGraph(attributePaths = {"vehicle", "driver"})
	List<Trip> findByOrgIdAndTripDateBetweenOrderByStartedAtAsc(Long orgId, LocalDate from, LocalDate to);

	@EntityGraph(attributePaths = {"vehicle", "driver"})
	List<Trip> findByOrgIdAndVehicleIdAndTripDateBetweenOrderByStartedAtAsc(Long orgId, Long vehicleId,
			LocalDate from, LocalDate to);

	/** 이 차량 트립 중 since 이후 관리자 수정 이력이 생긴 트립 (설정 내려받기 tripUpdates, rules §5) */
	@Query("""
			select t from Trip t
			where t.orgId = :orgId and t.vehicle.id = :vehicleId and exists (
				select 1 from TripEditLog e
				where e.tripId = t.id and e.actorType = com.autolog.server.trip.ActorType.ADMIN and e.createdAt >= :since)
			order by t.startedAt asc""")
	List<Trip> findAdminEditedSince(@Param("orgId") Long orgId, @Param("vehicleId") Long vehicleId,
			@Param("since") Instant since);
}
