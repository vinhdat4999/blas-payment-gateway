package com.blas.blaspaymentgateway.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blas.blas-payment-gateway.vnpay")
public class VnPayProperties {

  private String version;
  private String tmnCode;
  private String payUrl;
  private String returnUrl;
  private String checkOrderUrl;
  private int vnpTxnRefLength;
  private String orderType;
}
