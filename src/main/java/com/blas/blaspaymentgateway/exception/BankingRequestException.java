package com.blas.blaspaymentgateway.exception;

import com.blas.blaspaymentgateway.enums.BankingPaymentErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BankingRequestException extends RuntimeException {

  private final BankingPaymentErrorCode bankingPaymentErrorCode;

  public BankingRequestException(BankingPaymentErrorCode bankingPaymentErrorCode) {
    super(bankingPaymentErrorCode.getDescription());
    this.bankingPaymentErrorCode = bankingPaymentErrorCode;
  }

  public BankingRequestException(BankingPaymentErrorCode bankingPaymentErrorCode, String message) {
    super(message);
    this.bankingPaymentErrorCode = bankingPaymentErrorCode;
  }

  public BankingRequestException(BankingPaymentErrorCode bankingPaymentErrorCode, Throwable cause) {
    super(cause);
    this.bankingPaymentErrorCode = bankingPaymentErrorCode;
  }

  public BankingRequestException(BankingPaymentErrorCode bankingPaymentErrorCode, String message,
      Throwable cause) {
    super(message, cause);
    this.bankingPaymentErrorCode = bankingPaymentErrorCode;
  }
}
