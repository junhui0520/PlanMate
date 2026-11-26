package com.planmate.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * 로그인이 필요한 주소·세션·CSRF·CORS 설정 (ACC-07).
 * 서버 세션(Spring Session JDBC) + HttpOnly 쿠키, JWT 쓰지 않음. 로그인·로그아웃은 ACC-02 API로 한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler securityErrorHandler)
            throws Exception {
        http
                // WebConfig의 corsConfigurationSource 빈 사용 (프론트 주소만, 쿠키 포함)
                .cors(Customizer.withDefaults())
                // SPA용 CSRF: XSRF-TOKEN 쿠키를 화면이 읽어 X-XSRF-TOKEN 헤더로 보낸다
                .csrf(csrf -> csrf.spa())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        // 관리자는 두 겹으로 막는다: 여기서는 세션의 권한으로 빠르게 거르고,
                        // 실제 조치 전에는 AdminAccessChecker.requireAdmin()이 DB의 SYSTEM_ROLE을 다시 확인한다
                        // (로그인 뒤 관리자 권한이 빠져도 세션 권한은 다시 로그인할 때까지 남기 때문, ACC-07-03)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 공개 API가 더 필요하면 각 파트 contracts 확정 후 A가 여기에 추가 (PUB-03·04, ADM-03, COL-02-01 등)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                // 세션 시간 정책 (A-P08). 세션 고정 보호는 기본값(changeSessionId)
                .addFilterAfter(new SessionPolicyFilter(), SecurityContextHolderFilter.class);
        return http.build();
    }

    /**
     * 비밀번호 해시 (A-P02): bcrypt(위임 인코더 기본값).
     * Argon2는 BouncyCastle 의존성이 필요해서 쓰지 않는다.
     * 비밀번호는 ASCII 최대 64자라 bcrypt 72바이트 제한에 걸리지 않는다 (A-F01).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
