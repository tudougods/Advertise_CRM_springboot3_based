package com.internship.crm.config;

import com.internship.crm.auth.security.JwtAuthenticationFilter;
import com.internship.crm.auth.security.RestAccessDeniedHandler;
import com.internship.crm.auth.security.RestAuthenticationEntryPoint;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Stateless JWT authentication and role-based access rules for the CRM API. */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health/**",
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/payment-callbacks/mock",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/advertisers/*/account/consumptions")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/advertisers/*/account/transactions")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/advertisers/*/account")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/advertisers/**").hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/v1/advertisers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/advertiser-categories/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers("/api/v1/advertiser-categories/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/advertising-types/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/delivery-records")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.GET, "/api/v1/delivery-records/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/delivery-records/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/delivery-records/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/reports/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/payment-orders")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/payment-orders/*/simulate")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/payment-orders/**")
                    .hasAnyRole("ADMIN", "OPERATOR")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
