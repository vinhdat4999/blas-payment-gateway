package com.blas.blaspaymentgateway.dao;

import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripePaymentTransactionLogDao extends
    JpaRepository<StripePaymentTransactionLog, String> {

}
