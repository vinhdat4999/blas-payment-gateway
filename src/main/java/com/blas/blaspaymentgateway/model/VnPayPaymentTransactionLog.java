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
@Table(name = "vnpay_payment_transaction_logs")
public class VnPayPaymentTransactionLog {

  @Id
  @Column(name = "payment_transaction_log_id", length = 50, nullable = false)
  private String paymentTransactionLogId;

  @Column(name = "txn_ref", length = 50, nullable = false)
  private String txnRef;

  @Column(name = "transaction_time", nullable = false)
  private LocalDateTime transactionTime;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_vnpay_payment_transaction_log_1"))
  private AuthUser authUser;

  @Column(name = "command", nullable = false)
  private String command;

  @Column(name = "amount", nullable = false)
  private long amount;

  @Column(name = "currency", nullable = false)
  private String currency;

  @Column(name = "order_info", length = 200, nullable = false)
  private String orderInfo;

  @Column(name = "order_type", length = 10)
  @NotEmpty
  private String orderType;

  @Column(name = "locale", length = 5)
  private String locale;

  @Column(name = "ip_address", length = 20, nullable = false)
  private String ipAddress;

  @Column(name = "create_date", length = 14, nullable = false)
  private String createDate;

  @Column(name = "expire_date", length = 14, nullable = false)
  private String expireDate;

  @Column(name = "request_id", length = 50)
  private String requestId;

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

  @Column(name = "vnp_pay_date", length = 14, nullable = false)
  private String vnpPayDate;

  @Column(name = "vnp_response_id", length = 32, nullable = false)
  private String vnpResponseId;

  @Column(name = "vnp_tmn_code", length = 8, nullable = false)
  private String vnpTmnCode;

  @Column(name = "vnp_response_code", length = 45, nullable = false)
  private String vnpResponseCode;

  @Column(name = "vnp_message", nullable = false)
  private String vnpMessage;

  @Column(name = "vnp_bank_code", length = 20, nullable = false)
  private String vnpBankCode;

  @Column(name = "vnp_transaction_type", length = 2, nullable = false)
  private String vnpTransactionType;

  @Column(name = "vnp_transaction_status", length = 2, nullable = false)
  private String vnpTransactionStatus;

  @Column(name = "vnp_promotion_code", length = 12, nullable = false)
  private String vnpPromotionCode;

  @Column(name = "vnp_promotion_amount", nullable = false)
  private int vnpPromotionAmount;
}
