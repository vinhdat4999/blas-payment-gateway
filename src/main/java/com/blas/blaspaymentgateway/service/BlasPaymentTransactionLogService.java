package com.blas.blaspaymentgateway.service;

import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;

public interface BlasPaymentTransactionLogService {

  BlasPaymentTransactionLog createBlasPaymentTransactionLog(
      BlasPaymentTransactionLog blasPaymentTransactionLog);
}
