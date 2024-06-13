package com.blas.blaspaymentgateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blas.blas-payment-gateway.banking-result-host")
public class BankingResultHostProperties {

  private String endpointUrl;
  private String postJobType;

}
