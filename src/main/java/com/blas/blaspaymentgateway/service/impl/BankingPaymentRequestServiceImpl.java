package com.blas.blaspaymentgateway.service.impl;

import static com.blas.blascommon.utils.IdUtils.genMixID;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.BANKING_PAYMENT_ID_PREFIX;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.LENGTH_OF_BANKING_PAYMENT_CODE;

import com.blas.blaspaymentgateway.dao.BankingPaymentRequestDao;
import com.blas.blaspaymentgateway.model.BankingPaymentRequest;
import com.blas.blaspaymentgateway.service.BankingPaymentRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = {Exception.class, Throwable.class})
public class BankingPaymentRequestServiceImpl implements BankingPaymentRequestService {

  @Lazy
  private final BankingPaymentRequestDao bankingPaymentRequestDao;

  @Override
  public String generateBankingPaymentId() {
    String bankingPaymentId;
    do {
      bankingPaymentId = BANKING_PAYMENT_ID_PREFIX + genMixID(
          LENGTH_OF_BANKING_PAYMENT_CODE - BANKING_PAYMENT_ID_PREFIX.length());
    } while (bankingPaymentRequestDao.existsById(bankingPaymentId));
    return bankingPaymentId;
  }

  @Override
  public BankingPaymentRequest save(BankingPaymentRequest bankingPaymentRequest) {
    return bankingPaymentRequestDao.save(bankingPaymentRequest);
  }
}
