package com.blas.blaspaymentgateway.service;

import com.blas.blaspaymentgateway.model.BankingPaymentRequest;

public interface BankingPaymentRequestService {

  String generateBankingPaymentId();

  BankingPaymentRequest save(BankingPaymentRequest bankingPaymentRequest);

}
