package com.autolog.server.device;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverConsentRepository extends JpaRepository<DriverConsent, Long> {

	Optional<DriverConsent> findByDriverIdAndConsentVersion(Long driverId, String consentVersion);
}
