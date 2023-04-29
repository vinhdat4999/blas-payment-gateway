package com.blas.blaspaymentgateway.payload;

import com.blas.blascommon.enums.Currency;
import lombok.Data;

@Data
public class ChargeRequest {

  private long amount;
  private Currency currency;
  private String cardId;
  private String description;
}
