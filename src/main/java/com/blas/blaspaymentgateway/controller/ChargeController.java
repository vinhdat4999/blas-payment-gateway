package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.StringUtils.SPACE;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blaspaymentgateway.exception.types.PaymentException;
import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;
import com.blas.blaspaymentgateway.payload.ChargeRequest;
import com.blas.blaspaymentgateway.payload.ChargeResponse;
import com.blas.blaspaymentgateway.service.BlasPaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class ChargeController {

  @Lazy
  @Autowired
  private StripeService paymentsService;

  @Lazy
  @Autowired
  private CardService cardService;

  @Lazy
  @Autowired
  private AuthUserService authUserService;

  @Lazy
  @Autowired
  private BlasPaymentTransactionLogService blasPaymentTransactionLogService;

  @PostMapping(value = "/charge")
  public ResponseEntity<ChargeResponse> charge(@RequestBody ChargeRequest chargeRequest) {
    BlasPaymentTransactionLog blasPaymentTransactionLog = BlasPaymentTransactionLog.builder()
        .paymentTransactionLogId(genUUID())
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(getUsernameLoggedIn()))
        .amount(chargeRequest.getAmount())
        .currency(chargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(chargeRequest.getDescription())
        .build();
    if (!getUsernameLoggedIn().equals(
        cardService.getCardInfoByCardId(chargeRequest.getCardId()).getAuthUser().getUsername())) {
      blasPaymentTransactionLog.setLogMessage1(TRANSACTION_FAILED);
      blasPaymentTransactionLog.setLogMessage2("CARD ID: " + chargeRequest.getCardId());
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(), null,
          INVALID_CARD);
    }
    blasPaymentTransactionLog.setCard(cardService.getCardInfoByCardId(chargeRequest.getCardId()));
    Charge charge;
    try {
      charge = paymentsService.charge(chargeRequest);
      blasPaymentTransactionLog.setStripeTransactionId(charge.getId());
      blasPaymentTransactionLog.setAmount(charge.getAmountCaptured());
      blasPaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      blasPaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      blasPaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
    } catch (StripeException exception) {
      blasPaymentTransactionLog.setStripeTransactionId(exception.getStripeError().getCharge());
      blasPaymentTransactionLog.setLogMessage1(exception.toString());
      blasPaymentTransactionLog.setLogMessage2(exception.getMessage());
      blasPaymentTransactionLog.setLogMessage3(exception.getStripeError().toString());
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          blasPaymentTransactionLog.getStripeTransactionId(),
          exception.getMessage());
    } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
             InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException exception) {
      blasPaymentTransactionLog.setLogMessage1(exception.toString());
      blasPaymentTransactionLog.setLogMessage2(exception.getMessage());
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          blasPaymentTransactionLog.getStripeTransactionId(),
          exception.getMessage());
    }
    blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
    return ResponseEntity.ok(
        buildChargeResponse(blasPaymentTransactionLog.getPaymentTransactionLogId(), charge,
            chargeRequest.getCardId(), getUsernameLoggedIn()));
  }

  private ChargeResponse buildChargeResponse(String transactionId, Charge charge, String cardId,
      String username) {
    return ChargeResponse.builder()
        .transactionId(transactionId)
        .stripeTransactionId(charge.getId())
        .stripeTransactionId(charge.getId())
        .amountCaptured(
            (double) (charge.getAmountCaptured()) / 100 + SPACE + charge.getCurrency()
                .toUpperCase())
        .username(username)
        .cardId(cardId)
        .receiptUrl(charge.getReceiptUrl())
        .status(charge.getStatus().toUpperCase())
        .description(charge.getDescription())
        .transactionTime(
            LocalDateTime.ofEpochSecond(charge.getCreated(), 0, ZoneOffset.UTC).minusHours(-7))
        .build();
  }
}
