package com.autolog.server.device;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PairingCodeRepository extends JpaRepository<PairingCode, UUID> {

	boolean existsByCodeAndStatus(String code, PairingStatus status);

	/** 같은 코드 중 가장 최근 것 (PAIRED 제외). 페어링 시 행 잠금 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from PairingCode p where p.code = :code and p.status <> com.autolog.server.device.PairingStatus.PAIRED order by p.createdAt desc")
	List<PairingCode> findUnpairedByCodeForUpdate(@Param("code") String code);

	/** 유효시간이 지난 대기 코드를 EXPIRED로 정리해 코드 값을 재사용할 수 있게 한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update PairingCode p set p.status = com.autolog.server.device.PairingStatus.EXPIRED where p.status = com.autolog.server.device.PairingStatus.PENDING and p.expiresAt <= :now")
	int expirePending(@Param("now") Instant now);

	/** 같은 설치에서 새 코드를 받으면 이전 대기 코드는 만료시킨다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update PairingCode p set p.status = com.autolog.server.device.PairingStatus.EXPIRED where p.status = com.autolog.server.device.PairingStatus.PENDING and p.installationId = :installationId")
	int expirePendingByInstallation(@Param("installationId") String installationId);
}
