package com.blas.blaspaymentgateway.properties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckRequest {

  private String vnpTxnRef;
  private String transactionDate;
}
