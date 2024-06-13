package com.blas.blaspaymentgateway.enums;

import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.CAN_NOT_GET_PAYMENT_CODE;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.MESSAGING_EXCEPTION_DURING_IDLE;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.RECEIVED_STATUS;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BankingPaymentErrorCode {

  RECEIVED("00", RECEIVED_STATUS),
  PAYMENT_RECORDED("10", CAN_NOT_GET_PAYMENT_CODE),
  EXTRACTING_EMAIL_ERROR("11", "ERROR WHILE EXTRACTING EMAIL CONTENT"),
  USER_UNAUTHORIZED("12", "USER NOT AUTHORIZED TO ACCESS THIS RESOURCE"),
  IDLE_ERROR("13", MESSAGING_EXCEPTION_DURING_IDLE);

  private final String code;
  private final String description;

}
