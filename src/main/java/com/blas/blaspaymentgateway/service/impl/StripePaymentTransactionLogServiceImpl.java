package com.blas.blaspaymentgateway.service.impl;

import com.blas.blaspaymentgateway.dao.StripePaymentTransactionLogDao;
import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;
import com.blas.blaspaymentgateway.service.StripePaymentTransactionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = {Exception.class, Throwable.class})
public class StripePaymentTransactionLogServiceImpl implements StripePaymentTransactionLogService {

  @Lazy
  private final StripePaymentTransactionLogDao stripePaymentTransactionLogDao;

  @Override
  public StripePaymentTransactionLog createStripePaymentTransactionLog(
      StripePaymentTransactionLog stripePaymentTransactionLog) {
    return stripePaymentTransactionLogDao.save(stripePaymentTransactionLog);
  }

  @Override
  public boolean isExistedId(String stripePaymentTransactionLogId) {
    return stripePaymentTransactionLogDao.findById(stripePaymentTransactionLogId).isPresent();
  }
}
