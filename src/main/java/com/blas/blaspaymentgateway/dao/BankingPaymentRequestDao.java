package com.blas.blaspaymentgateway.dao;

import com.blas.blaspaymentgateway.model.BankingPaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankingPaymentRequestDao extends JpaRepository<BankingPaymentRequest, String> {

}
