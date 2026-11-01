package com.autolog.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 인증은 관리자 JWT·기기 토큰 필터로만 한다. 기본 사용자(생성 비밀번호 로그 출력)는 만들지 않는다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

}
