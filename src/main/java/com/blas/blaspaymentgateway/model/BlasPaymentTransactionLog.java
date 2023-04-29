package com.blas.blaspaymentgateway.model;

import com.blas.blascommon.core.model.AuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blas_payment_transaction_logs")
public class BlasPaymentTransactionLog {

  @Id
  @Column(name = "payment_transaction_log_id", length = 50, nullable = false)
  private String paymentTransactionLogId;

  @Column(name = "stripe_transaction_id", length = 200, nullable = false)
  private String stripeTransactionId;

  @Column(name = "transaction_time", nullable = false)
  private LocalDateTime transactionTime;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "card_id", foreignKey = @ForeignKey(name = "fk_blas_payment_transaction_log_1"))
  private Card card;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_blas_payment_transaction_log_2"))
  private AuthUser authUser;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "receipt_url", length = 200, nullable = false)
  private String receipt_url;

  @Column(name = "status", length = 20, nullable = false)
  @NotEmpty
  private String status;

  @Column(name = "description", length = 255, nullable = false)
  private String description;

  @Column(name = "card_type", length = 10, nullable = false)
  private String cardType;

  @Column(name = "log_message_1", nullable = false)
  private String logMessage1;

  @Column(name = "log_message_2", nullable = false)
  private String logMessage2;

  @Column(name = "log_message_3", nullable = false)
  private String logMessage3;

  @Column(name = "log_message_4", nullable = false)
  private String logMessage4;

  @Column(name = "log_message_5", nullable = false)
  private String logMessage5;
}
