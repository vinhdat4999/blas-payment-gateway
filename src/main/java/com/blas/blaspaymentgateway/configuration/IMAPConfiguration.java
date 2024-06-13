package com.blas.blaspaymentgateway.configuration;

import com.blas.blaspaymentgateway.properties.ImapConfigurationProperties;
import jakarta.mail.Session;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@RequiredArgsConstructor
public class IMAPConfiguration {

  @Lazy
  private final ImapConfigurationProperties imapConfigurationProperties;

  @Bean
  public Session mailSession() {
    Properties props = new Properties();
    props.setProperty("mail.store.protocol", "imaps");
    props.setProperty("mail.imaps.host", imapConfigurationProperties.getHost());
    props.setProperty("mail.imaps.port", String.valueOf(imapConfigurationProperties.getPort()));
    return Session.getInstance(props);
  }
}
