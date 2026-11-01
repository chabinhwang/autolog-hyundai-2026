package com.autolog.server.device;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

	Optional<Device> findByTokenHash(String tokenHash);

	boolean existsByTokenHash(String tokenHash);

	Optional<Device> findByVehicleIdAndRevokedAtIsNull(Long vehicleId);

	List<Device> findByVehicleIdInAndRevokedAtIsNull(Collection<Long> vehicleIds);

	/** 같은 회사에서 같은 앱 설치가 쓰던 활성 기기 (다른 차량으로 옮겨 페어링할 때 폐기) */
	List<Device> findByOrgIdAndInstallationIdAndRevokedAtIsNull(Long orgId, String installationId);

	/** 차량별 마지막 동기화 시각 (연결 해제된 기기 포함) */
	@Query("select d.vehicleId, max(d.lastSyncedAt) from Device d where d.vehicleId in :vehicleIds group by d.vehicleId")
	List<Object[]> findLastSyncedAtByVehicleIds(@Param("vehicleIds") Collection<Long> vehicleIds);

	default Map<Long, Instant> lastSyncedAtByVehicle(Collection<Long> vehicleIds) {
		Map<Long, Instant> result = new HashMap<>();
		if (vehicleIds.isEmpty()) {
			return result;
		}
		for (Object[] row : findLastSyncedAtByVehicleIds(vehicleIds)) {
			if (row[1] != null) {
				result.put((Long) row[0], (Instant) row[1]);
			}
		}
		return result;
	}
}
