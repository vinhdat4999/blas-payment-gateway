package com.blas.blaspaymentgateway.service;

import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;

public interface StripePaymentTransactionLogService {

  StripePaymentTransactionLog createStripePaymentTransactionLog(
      StripePaymentTransactionLog stripePaymentTransactionLog);

  boolean isExistedId(String stripePaymentTransactionLogId);
}
