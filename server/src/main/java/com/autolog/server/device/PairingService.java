package com.autolog.server.device;

import com.autolog.server.device.DeviceDtos.PairRequest;
import com.autolog.server.device.DeviceDtos.PairResponse;
import com.autolog.server.device.DeviceDtos.PairedVehicle;
import com.autolog.server.device.DeviceDtos.PairingCodeRequest;
import com.autolog.server.device.DeviceDtos.PairingCodeResponse;
import com.autolog.server.device.DeviceDtos.PairingStatusResponse;
import com.autolog.server.device.DeviceDtos.UnpairResponse;
import com.autolog.server.global.error.BusinessException;
import com.autolog.server.global.error.ErrorCode;
import com.autolog.server.global.time.Times;
import com.autolog.server.masterdata.Vehicle;
import com.autolog.server.masterdata.VehicleRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 페어링 (rules §2, D6): 6자리 코드, 10분 유효, 1회용. 차량 1대 = 활성 기기 1개 */
@Service
public class PairingService {

	public static final Duration CODE_TTL = Duration.ofMinutes(10);
	private static final int CODE_ATTEMPTS = 20;

	private final PairingCodeRepository pairingCodeRepository;
	private final DeviceRepository deviceRepository;
	private final VehicleRepository vehicleRepository;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public PairingService(PairingCodeRepository pairingCodeRepository, DeviceRepository deviceRepository,
			VehicleRepository vehicleRepository, Clock clock) {
		this.pairingCodeRepository = pairingCodeRepository;
		this.deviceRepository = deviceRepository;
		this.vehicleRepository = vehicleRepository;
		this.clock = clock;
	}

	@Transactional
	public PairingCodeResponse issue(PairingCodeRequest request) {
		Instant now = Times.now(clock);
		// 만료된 대기 코드를 정리해 코드 값을 다시 쓸 수 있게 하고, 같은 설치의 이전 대기 코드는 무효로 한다.
		pairingCodeRepository.expirePending(now);
		pairingCodeRepository.expirePendingByInstallation(request.installationId());
		String code = newCode();
		PairingCode pairing = new PairingCode(UUID.randomUUID(), code, request.installationId(), request.tokenHash(),
				now, now.plus(CODE_TTL));
		pairingCodeRepository.saveAndFlush(pairing);
		return new PairingCodeResponse(pairing.getId(), pairing.getCode(), Times.toSeoul(pairing.getExpiresAt()));
	}

	@Transactional(readOnly = true)
	public PairingStatusResponse status(String pairingId) {
		PairingCode pairing = parseUuid(pairingId).flatMap(pairingCodeRepository::findById)
				.orElseThrow(() -> new BusinessException(ErrorCode.PAIRING_NOT_FOUND));
		PairingStatus status = pairing.effectiveStatus(Times.now(clock));
		PairedVehicle vehicle = null;
		if (status == PairingStatus.PAIRED && pairing.getVehicleId() != null) {
			vehicle = vehicleRepository.findById(pairing.getVehicleId())
					.map(found -> new PairedVehicle(found.getRegNo(), found.getModelName()))
					.orElse(null);
		}
		return new PairingStatusResponse(status, Times.toSeoul(pairing.getExpiresAt()), vehicle);
	}

	@Transactional
	public PairResponse pair(Long orgId, PairRequest request) {
		Vehicle vehicle = vehicleRepository.findByIdAndOrgIdAndActiveTrue(request.vehicleId(), orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
		Instant now = Times.now(clock);
		List<PairingCode> candidates = pairingCodeRepository.findUnpairedByCodeForUpdate(request.code());
		if (candidates.isEmpty()) {
			throw new BusinessException(ErrorCode.PAIRING_CODE_INVALID);
		}
		PairingCode pairing = candidates.getFirst();
		if (pairing.effectiveStatus(now) != PairingStatus.PENDING) {
			throw new BusinessException(ErrorCode.PAIRING_CODE_EXPIRED);
		}
		if (deviceRepository.existsByTokenHash(pairing.getTokenHash())) {
			// 이미 쓰인 토큰 해시 (차량 앱은 페어링마다 새 토큰을 만든다)
			throw new BusinessException(ErrorCode.PAIRING_CODE_INVALID);
		}
		Long replacedDeviceId = null;
		Optional<Device> existing = deviceRepository.findByVehicleIdAndRevokedAtIsNull(vehicle.getId());
		if (existing.isPresent()) {
			existing.get().revoke(now);
			replacedDeviceId = existing.get().getId();
		}
		// 같은 앱 설치가 회사의 다른 차량에 연결돼 있었다면 그 기기도 폐기한다 (차량 1대 = 활성 기기 1개, 설치 1개 = 활성 차량 1대).
		for (Device other : deviceRepository.findByOrgIdAndInstallationIdAndRevokedAtIsNull(orgId,
				pairing.getInstallationId())) {
			other.revoke(now);
		}
		// 부분 unique(vehicle_id where revoked_at is null)를 지키기 위해 새 기기보다 먼저 반영한다.
		deviceRepository.flush();
		Device device = deviceRepository.saveAndFlush(new Device(orgId, vehicle.getId(), pairing.getInstallationId(),
				pairing.getTokenHash(), now));
		pairing.pair(vehicle.getId(), now);
		return new PairResponse(device.getId(), vehicle.getId(), Times.toSeoul(now), replacedDeviceId);
	}

	@Transactional
	public UnpairResponse unpair(Long orgId, Long vehicleId) {
		Vehicle vehicle = vehicleRepository.findByIdAndOrgId(vehicleId, orgId)
				.orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_NOT_FOUND));
		Device device = deviceRepository.findByVehicleIdAndRevokedAtIsNull(vehicle.getId())
				.orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
		Instant now = Times.now(clock);
		device.revoke(now);
		return new UnpairResponse(vehicle.getId(), device.getId(), Times.toSeoul(now));
	}

	/** 대기 중인 코드와 겹치지 않는 6자리 숫자 */
	private String newCode() {
		for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
			String code = String.format("%06d", random.nextInt(1_000_000));
			if (!pairingCodeRepository.existsByCodeAndStatus(code, PairingStatus.PENDING)) {
				return code;
			}
		}
		throw new IllegalStateException("페어링 코드를 만들지 못했습니다.");
	}

	private static Optional<UUID> parseUuid(String value) {
		try {
			return Optional.of(UUID.fromString(value));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
