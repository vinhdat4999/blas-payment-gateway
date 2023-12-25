package com.blas.blaspaymentgateway.dao;

import com.blas.blaspaymentgateway.model.VnPayPaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VnPayPaymentTransactionLogDao extends
    JpaRepository<VnPayPaymentTransactionLog, String> {

}
