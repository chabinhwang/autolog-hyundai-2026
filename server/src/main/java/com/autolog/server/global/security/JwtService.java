package com.autolog.server.global.security;

import com.autolog.server.global.config.AppProperties;
import com.autolog.server.global.time.Times;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 JWT (D1). HS256, 클레임 sub(adminUserId)·orgId·role(ADMIN)·exp, Refresh 없음.
 */
@Component
public class JwtService {

	private static final String CLAIM_ORG_ID = "orgId";
	private static final String CLAIM_ROLE = "role";

	private final JwtEncoder encoder;
	private final JwtDecoder decoder;
	private final Clock clock;
	private final Duration expiresIn;

	public JwtService(AppProperties properties, Clock clock) {
		SecretKey key = new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
		timestampValidator.setClock(clock);
		nimbusDecoder.setJwtValidator(timestampValidator);
		this.decoder = nimbusDecoder;
		this.clock = clock;
		this.expiresIn = Duration.ofHours(properties.jwt().expiresHours());
	}

	public Duration expiresIn() {
		return expiresIn;
	}

	public IssuedToken issue(Long adminUserId, Long orgId) {
		Instant expiresAt = Times.now(clock).plus(expiresIn);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(String.valueOf(adminUserId))
				.claim(CLAIM_ORG_ID, orgId)
				.claim(CLAIM_ROLE, AutologAuthentication.ROLE_ADMIN)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(token, expiresAt);
	}

	/** 서명·만료·클레임이 모두 맞으면 관리자 주체, 아니면 빈 값 */
	public Optional<AdminPrincipal> verify(String token) {
		try {
			Jwt jwt = decoder.decode(token);
			if (jwt.getExpiresAt() == null || !AutologAuthentication.ROLE_ADMIN.equals(jwt.getClaimAsString(CLAIM_ROLE))) {
				return Optional.empty();
			}
			Object orgId = jwt.getClaim(CLAIM_ORG_ID);
			if (!(orgId instanceof Number orgNumber) || jwt.getSubject() == null) {
				return Optional.empty();
			}
			return Optional.of(new AdminPrincipal(Long.valueOf(jwt.getSubject()), orgNumber.longValue(),
					jwt.getExpiresAt()));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}
}
