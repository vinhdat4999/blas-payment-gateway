package com.blas.blaspaymentgateway.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VnPayCommand {
  PAY("pay"),
  QUERYDR("querydr");

  private final String command;
}
