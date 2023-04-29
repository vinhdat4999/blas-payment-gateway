package com.blas.blaspaymentgateway.payload;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChargeResponse {

  private String transactionId;
  private LocalDateTime transactionTime;
  private String cardId;
  private String username;
  private String amountCaptured;
  private String receiptUrl;
  private String status;
  private String description;
}
