package com.blas.blaspaymentgateway.service.impl;

import static com.blas.blascommon.constants.ResponseMessage.PAYMENT_TRANSACTION_LOG_ID_NOT_FOUND;

import com.blas.blascommon.exceptions.types.NotFoundException;
import com.blas.blaspaymentgateway.dao.VnPayPaymentTransactionLogDao;
import com.blas.blaspaymentgateway.model.VnPayPaymentTransactionLog;
import com.blas.blaspaymentgateway.service.VnPayPaymentTransactionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = {Exception.class, Throwable.class})
public class VnPayPaymentTransactionLogServiceImpl implements VnPayPaymentTransactionLogService {

  @Lazy
  private final VnPayPaymentTransactionLogDao vnPayPaymentTransactionLogDao;

  @Override
  public VnPayPaymentTransactionLog createVnPayPaymentTransactionLog(
      VnPayPaymentTransactionLog vnPayPaymentTransactionLog) {
    return vnPayPaymentTransactionLogDao.save(vnPayPaymentTransactionLog);
  }

  @Override
  public VnPayPaymentTransactionLog getVnPayPaymentTransactionLog(
      String vnpayPaymentTransactionLogId) {
    return vnPayPaymentTransactionLogDao.findById(vnpayPaymentTransactionLogId)
        .orElseThrow(() -> new NotFoundException(PAYMENT_TRANSACTION_LOG_ID_NOT_FOUND));
  }

  @Override
  public void saveVnPayPaymentTransactionLog(
      VnPayPaymentTransactionLog vnPayPaymentTransactionLog) {
    vnPayPaymentTransactionLogDao.save(vnPayPaymentTransactionLog);
  }
}
