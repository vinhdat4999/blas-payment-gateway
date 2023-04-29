package com.blas.blaspaymentgateway.dao;

import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlasPaymentTransactionLogDao extends
    JpaRepository<BlasPaymentTransactionLog, String> {

}
