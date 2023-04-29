package com.blas.blaspaymentgateway.service.impl;

import com.blas.blaspaymentgateway.dao.BlasPaymentTransactionLogDao;
import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;
import com.blas.blaspaymentgateway.service.BlasPaymentTransactionLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = {Exception.class, Throwable.class})
public class BlasPaymentTransactionLogServiceImpl implements BlasPaymentTransactionLogService {

  @Lazy
  @Autowired
  private BlasPaymentTransactionLogDao blasPaymentTransactionLogDao;

  @Override
  public BlasPaymentTransactionLog createBlasPaymentTransactionLog(
      BlasPaymentTransactionLog blasPaymentTransactionLog) {
    return blasPaymentTransactionLogDao.save(blasPaymentTransactionLog);
  }
}
