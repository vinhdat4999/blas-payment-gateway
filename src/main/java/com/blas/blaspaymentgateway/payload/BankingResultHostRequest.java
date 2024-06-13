package com.blas.blaspaymentgateway.payload;

import com.blas.blaspaymentgateway.model.BankingPaymentRequest;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BankingResultHostRequest {

  private String postJobType;
  private String username;
  private String password;
  private BankingPaymentRequest bankingPaymentRequest;

}
