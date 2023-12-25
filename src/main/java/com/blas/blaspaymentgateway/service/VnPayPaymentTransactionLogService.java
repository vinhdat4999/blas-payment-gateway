package com.blas.blaspaymentgateway.service;

import com.blas.blaspaymentgateway.model.VnPayPaymentTransactionLog;

public interface VnPayPaymentTransactionLogService {

  VnPayPaymentTransactionLog createVnPayPaymentTransactionLog(
      VnPayPaymentTransactionLog vnPayPaymentTransactionLog);

  VnPayPaymentTransactionLog getVnPayPaymentTransactionLog(String vnpayPaymentTransactionLogId);

  void saveVnPayPaymentTransactionLog(VnPayPaymentTransactionLog vnPayPaymentTransactionLog);
}
