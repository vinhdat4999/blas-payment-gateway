package com.blas.blaspaymentgateway.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "banking_payment_requests")
public class BankingPaymentRequest {

  @Id
  @Column(name = "payment_id", length = 10, nullable = false)
  @NotEmpty
  private String paymentId;

  @Column(name = "blas_process_time")
  private LocalDateTime blasProcessTime;

  @Column(name = "received_time")
  private Date receivedTime;

  @Column(name = "amount")
  private int amount;

  @Column(name = "payment_code", length = 10)
  private String paymentCode;

  @Column(name = "full_description", length = 500)
  private String fullDescription;

  @Column(name = "note", length = 200)
  private String note;

  @Column(name = "post_status", length = 500)
  private String postStatus;

  @Column(name = "host_response")
  private String hostResponse;
}
