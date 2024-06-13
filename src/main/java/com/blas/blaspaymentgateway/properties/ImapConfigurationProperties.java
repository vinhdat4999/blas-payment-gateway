package com.blas.blaspaymentgateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blas.blas-payment-gateway.imap")
public class ImapConfigurationProperties {

  private String host;
  private int port;

}
