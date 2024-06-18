package com.blas.blaspaymentgateway.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class IMAPBankingPayment {

  public static final String IMAPS = "imaps";
  public static final String IDLE_CONNECTION_KEEP_ALIVE = "IdleConnectionKeepAlive";
  public static final String INBOX = "INBOX";
  public static final String BANKING_PAYMENT_ID_PREFIX = "BLAS";
  public static final int LENGTH_OF_BANKING_PAYMENT_CODE = 10;
  public static final String RECEIVED_STATUS = "SUCCESSFUL PAYMENT RECORDED";
  public static final String CAN_NOT_GET_PAYMENT_CODE = "CAN NOT GET PAYMENT CODE";
  public static final String MESSAGING_EXCEPTION_DURING_IDLE = "Messaging exception during IDLE";
  public static final String POST_STATUS_NOT_YET = "NOT SENT YET";

}
