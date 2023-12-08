package com.blas.blaspaymentgateway.configuration;

import com.blas.blascommon.jwt.JwtAuthenticationEntryPoint;
import com.blas.blascommon.jwt.JwtRequestFilter;
import com.blas.blascommon.security.hash.Sha256Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

  private final Sha256Encoder sha256Encoder;

  @Bean
  public AuthenticationManager authenticationManager(HttpSecurity http,
      UserDetailsService jwtUserDetailsService) throws Exception {
    AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(
        AuthenticationManagerBuilder.class);
    authenticationManagerBuilder.userDetailsService(jwtUserDetailsService)
        .passwordEncoder(sha256Encoder);
    return authenticationManagerBuilder.build();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtRequestFilter jwtRequestFilter,
      JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(authorize -> authorize.requestMatchers("/auth/**", "/actuator/**")
            .permitAll()
            .anyRequest()
            .hasAnyRole("ADMIN", "BOD", "MAINTAINER", "MANAGER", "USER"));
    http.exceptionHandling(
            authorize -> authorize.authenticationEntryPoint(jwtAuthenticationEntryPoint))
        .sessionManagement(
            authorize -> authorize.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedMethods("*");
      }
    };
  }
}
