package com.autolog.server.global.security;

import com.autolog.server.device.DeviceRepository;
import com.autolog.server.global.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증·인가 규칙.
 * <ul>
 * <li>공개: 로그인, 페어링 코드 발급·상태 조회, /actuator/health</li>
 * <li>차량(기기 토큰): /devices/me/**, PUT /trips/batch</li>
 * <li>차량·관리자 공용: POST /trips/{clientUuid}/edits</li>
 * <li>그 외 /api/v1/** 는 관리자 JWT</li>
 * </ul>
 * 쿠키·세션을 쓰지 않으므로 CSRF는 끈다.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	private static final String API = "/api/v1";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
			DeviceRepository deviceRepository, SecurityErrorWriter errorWriter) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.addFilterBefore(new BearerTokenAuthenticationFilter(jwtService, deviceRepository),
						AnonymousAuthenticationFilter.class)
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new JsonAuthenticationEntryPoint(errorWriter))
						.accessDeniedHandler(new JsonAccessDeniedHandler(errorWriter)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll()
						.requestMatchers(HttpMethod.POST, API + "/devices/pairing-codes").permitAll()
						.requestMatchers(HttpMethod.GET, API + "/devices/pairing-codes/*").permitAll()
						.requestMatchers(API + "/devices/me/**").hasRole(AutologAuthentication.ROLE_DEVICE)
						.requestMatchers(HttpMethod.PUT, API + "/trips/batch").hasRole(AutologAuthentication.ROLE_DEVICE)
						.requestMatchers(HttpMethod.POST, API + "/trips/*/edits")
						.hasAnyRole(AutologAuthentication.ROLE_ADMIN, AutologAuthentication.ROLE_DEVICE)
						.requestMatchers(API + "/**").hasRole(AutologAuthentication.ROLE_ADMIN)
						.anyRequest().authenticated());
		return http.build();
	}

	/** D8: 허용 출처는 CORS_ALLOWED_ORIGINS. 자격 증명(쿠키) 없음 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(properties.cors().allowedOrigins());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
		config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
		// 기록부 다운로드 파일명을 대시보드(fetch)가 읽을 수 있게 노출한다.
		config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
		config.setAllowCredentials(false);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
