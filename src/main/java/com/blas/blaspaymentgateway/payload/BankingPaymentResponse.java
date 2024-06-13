package com.blas.blaspaymentgateway.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankingPaymentResponse {

  private int debt;
  private int paid;
  private String message;
  private int httpCode;
  private int errorCode;
  private String createTime;
  private String paymentCode;
  private String lastPaymentTime;

}
