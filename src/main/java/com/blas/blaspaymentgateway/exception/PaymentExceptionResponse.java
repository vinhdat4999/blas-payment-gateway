package com.blas.blaspaymentgateway.exception;

import com.blas.blascommon.exceptions.ExceptionResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentExceptionResponse extends ExceptionResponse {

  private String transactionId;
  private String stripeTransactionId;

}
