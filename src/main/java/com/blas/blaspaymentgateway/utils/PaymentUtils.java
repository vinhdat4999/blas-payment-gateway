package com.blas.blaspaymentgateway.utils;

import static com.blas.blascommon.utils.IdUtils.genMixID;
import static com.blas.blascommon.utils.StringUtils.SPACE;
import static org.springframework.http.HttpStatus.OK;

import com.blas.blascommon.payload.ChargeResponse;
import com.blas.blaspaymentgateway.service.BlasPaymentTransactionLogService;
import com.stripe.model.Charge;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PaymentUtils {

  public static String maskCardNumber(String cardNumber) {
    return cardNumber.replaceAll("^.{1,12}", "*".repeat(12));
  }

  public static String genTransactionId(
      BlasPaymentTransactionLogService blasPaymentTransactionLogService, int lengthOfId) {
    String transactionId;
    do {
      transactionId = genMixID(lengthOfId).toUpperCase();
    } while (blasPaymentTransactionLogService.isExistedId(transactionId));
    return transactionId;
  }


  public static ChargeResponse buildChargeResponse(String transactionId, Charge charge,
      String cardId,
      String cardNumber, boolean isGuestCard, String username) {
    String currency = charge.getCurrency();
    return ChargeResponse.builder()
        .statusCode(String.valueOf(OK.value()))
        .transactionId(transactionId)
        .transactionTime(
            LocalDateTime.ofEpochSecond(charge.getCreated(), 0, ZoneOffset.UTC).minusHours(-7))
        .cardId(cardId)
        .maskedCardNumber(maskCardNumber(cardNumber))
        .cardType(charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase())
        .username(username)
        .amountCaptured(getReformattedAmount(charge.getAmountCaptured(), currency))
        .amountRefund(getReformattedAmount(charge.getAmountRefunded(), currency))
        .status(charge.getStatus().toUpperCase())
        .isRefundTransaction(charge.getRefunded())
        .isGuestCard(isGuestCard)
        .description(charge.getDescription())
        .build();
  }

  private static String getReformattedAmount(Long amount, String currency) {
    return (double) (amount) / 100 + SPACE + currency.toUpperCase();
  }
}
