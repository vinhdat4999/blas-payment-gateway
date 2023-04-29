package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.StringUtils.SPACE;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.exceptions.types.BadGatewayException;
import com.blas.blascommon.exceptions.types.BadRequestException;
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
  public ResponseEntity<?> charge(@RequestBody ChargeRequest chargeRequest) {
    BlasPaymentTransactionLog blasPaymentTransactionLog = BlasPaymentTransactionLog.builder()
        .paymentTransactionLogId(genUUID())
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(getUsernameLoggedIn()))
        .amount(chargeRequest.getAmount())
        .status(TRANSACTION_FAILED)
        .description(chargeRequest.getDescription())
        .build();
    if (!getUsernameLoggedIn().equals(
        cardService.getCardInfoByCardId(chargeRequest.getCardId()).getAuthUser().getUsername())) {
      throw new BadRequestException(INVALID_CARD);
    }
    blasPaymentTransactionLog.setCard(cardService.getCardInfoByCardId(chargeRequest.getCardId()));
    Charge charge;
    try {
      charge = paymentsService.charge(chargeRequest);
      blasPaymentTransactionLog.setStripeTransactionId(charge.getId());
      blasPaymentTransactionLog.setAmount(charge.getAmountCaptured());
      blasPaymentTransactionLog.setReceipt_url(charge.getReceiptUrl());
      blasPaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      blasPaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
    } catch (StripeException exception) {
      blasPaymentTransactionLog.setLogMessage1(exception.toString());
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new BadGatewayException(exception.getMessage());
    } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
             InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException exception) {
      blasPaymentTransactionLog.setLogMessage1(exception.toString());
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new BadRequestException(exception.getMessage());
    }
    blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
    return ResponseEntity.ok(
        buildChargeResponse(charge, chargeRequest.getCardId(), getUsernameLoggedIn()));
  }

  private ChargeResponse buildChargeResponse(Charge charge, String cardId, String username) {
    return ChargeResponse.builder()
        .transactionId(charge.getId())
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
