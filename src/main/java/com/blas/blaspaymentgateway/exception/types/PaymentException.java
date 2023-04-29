package com.blas.blaspaymentgateway.exception.types;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentException extends RuntimeException {

  private String transactionId;
  private String stripeTransactionId;

  public PaymentException(String transactionId, String stripeTransactionId, String message,
      Throwable cause) {
    super(message, cause);
    this.transactionId = transactionId;
    this.stripeTransactionId = stripeTransactionId;
  }

  public PaymentException(String transactionId, String stripeTransactionId, String message) {
    super(message);
    this.transactionId = transactionId;
    this.stripeTransactionId = stripeTransactionId;
  }

  public PaymentException(Throwable cause) {
    super(cause);
  }
}
