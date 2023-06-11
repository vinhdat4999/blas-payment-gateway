package com.blas.blaspaymentgateway.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentGateway {

  public static final String STRIPE_PRIVATE_KEY = "STRIPE_PRIVATE_KEY";
  public static final String CARD_ID_NOT_FOUND = "CARD_ID_NOT_FOUND";
  public static final String CARD_EXISTED = "This card was already added before";
  public static final String INVALID_CARD = "INVALID CARD";
  public static final String INACTIVE_CARD = "INACTIVE CARD";
  public static final String TRANSACTION_FAILED = "FAILED";
  public static final String CARD_ADDED_SUCCESSFULLY = "Card successfully added";
  public static final String CARD_ID_SPACE_LABEL = "CARD ID: ";
  public static final String SUBJECT_EMAIL_RECEIPT = "[BLAS] BLAS PAYMENT RECEIPT";
  public static final String SUBJECT_ADD_NEW_CARD_SUCCESSFULLY = "[BLAS] ADD NEW CARD SUCCESSFULLY";
}
