package com.blas.blaspaymentgateway.utils;

import static com.blas.blascommon.utils.IdUtils.genMixID;

import com.blas.blaspaymentgateway.service.BlasPaymentTransactionLogService;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentUtils {

  public static String maskCardNumber(String cardNumber) {
    return cardNumber.replaceAll("^.{1,12}", "*".repeat(12));
  }

  public static String genTransactionId(
      BlasPaymentTransactionLogService blasPaymentTransactionLogService, int lengthOfId) {
    String transactionId;
    do {
      transactionId = genMixID(lengthOfId).toUpperCase();
    } while (blasPaymentTransactionLogService.isExistedId(transactionId));
    return transactionId;
  }
}
