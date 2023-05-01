package com.blas.blaspaymentgateway.utils;

import static com.blas.blascommon.utils.IdUtils.genMixID;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentUtils {

  public static String maskCardNumber(String cardNumber) {
    return cardNumber.replaceAll("^.{1,12}", "*".repeat(12));
  }

  public static String genTransactionId() {
    return genMixID(10);
  }
}
