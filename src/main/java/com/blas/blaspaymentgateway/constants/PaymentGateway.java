package com.blas.blaspaymentgateway.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentGateway {

  public static final String CARD_ID_NOT_FOUND = "CARD ID NOT FOUND";
  public static final String CARD_EXISTED = "THIS CARD ALREADY ADDED BEFORE";
  public static final String INVALID_CARD = "INVALID CARD";
  public static final String INACTIVE_CARD = "INACTIVE CARD";
  public static final String TRANSACTION_FAILED = "FAILED";
  public static final String CARD_ADDED_SUCCESSFULLY = "CARD SUCCESSFULLY ADDED";
  public static final String SUBJECT_EMAIL_RECEIPT = "[BLAS] BLAS PAYMENT RECEIPT";
  public static final String SUBJECT_ADD_NEW_CARD_SUCCESSFULLY = "[BLAS] ADD NEW CARD SUCCESSFULLY";
  public static final String CARD_ID_MDC_KEY = "card.id";
}
