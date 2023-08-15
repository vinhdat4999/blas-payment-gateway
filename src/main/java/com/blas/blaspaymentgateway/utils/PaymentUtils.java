package com.blas.blaspaymentgateway.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentUtils {

  public static String maskCardNumber(String cardNumber) {
    return cardNumber.replaceAll("^.{1,12}", "*".repeat(12));
  }
}
