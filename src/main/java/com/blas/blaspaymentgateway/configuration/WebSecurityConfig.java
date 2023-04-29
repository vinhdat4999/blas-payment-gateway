package com.blas.blaspaymentgateway.configuration;

import com.blas.blascommon.jwt.JwtAuthenticationEntryPoint;
import com.blas.blascommon.jwt.JwtRequestFilter;
import com.blas.blascommon.security.hash.Sha256Encoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

  @Bean
  public Sha256Encoder passwordEncoder() {
    return new Sha256Encoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(HttpSecurity http,
      UserDetailsService jwtUserDetailsService) throws Exception {
    AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(
        AuthenticationManagerBuilder.class);
    authenticationManagerBuilder.userDetailsService(jwtUserDetailsService)
        .passwordEncoder(passwordEncoder());
    return authenticationManagerBuilder.build();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtRequestFilter jwtRequestFilter,
      JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) throws Exception {
    http.csrf().disable();
    http.authorizeHttpRequests()
        .anyRequest()
        .hasAnyRole("ADMIN", "BOD", "MAINTAINER", "MANAGER", "USER")
        .and()
        .exceptionHandling()
        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
        .and()
        .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
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
