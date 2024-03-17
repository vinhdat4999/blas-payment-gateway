package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_BLAS_APP_FAILURE;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FAILURE;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INACTIVE_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.payment.ChargeResponse;
import com.blas.blascommon.payload.payment.StripeChargeRequest;
import com.blas.blascommon.security.KeyService;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.StripePaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.merchants.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AddedCardChargeController extends ChargeController {

  public AddedCardChargeController(AuthUserService authUserService, StripeService stripeService,
      CardService cardService, KeyService keyService, CentralizedLogService centralizedLogService,
      JwtTokenUtil jwtTokenUtil, StripeService paymentsService,
      StripePaymentTransactionLogService stripePaymentTransactionLogService,
      EmailQueueService emailQueueService) {
    super(authUserService, stripeService, cardService, keyService, centralizedLogService,
        jwtTokenUtil, paymentsService, stripePaymentTransactionLogService, emailQueueService);
  }

  @PostMapping(value = "/charge")
  public ResponseEntity<ChargeResponse> charge(@RequestBody StripeChargeRequest chargeRequest) {
    log.debug("Start transaction...");
    String username = getUsernameLoggedIn();
    StripePaymentTransactionLog stripePaymentTransactionLog = StripePaymentTransactionLog.builder()
        .paymentTransactionLogId(genTransactionId(stripePaymentTransactionLogService, lengthOfId))
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(username))
        .currency(chargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(chargeRequest.getDescription())
        .build();
    log.info(
        "blasPaymentTransactionLogId: " + stripePaymentTransactionLog.getPaymentTransactionLogId());
    String cardId = chargeRequest.getCardId();
    Card card = cardService.getCardInfoByCardId(cardId, true);
    if (!username.equals(card.getAuthUser().getUsername())) {
      stripePaymentTransactionLog.setLogMessage1(INVALID_CARD);
      stripePaymentTransactionLog.setCard(card);
      stripePaymentTransactionLogService.createStripePaymentTransactionLog(
          stripePaymentTransactionLog);
      throw new PaymentException(MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(), INVALID_CARD);
    }
    if (!card.isActive()) {
      throw new PaymentException(MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(), INACTIVE_CARD);
    }
    stripePaymentTransactionLog.setCard(card);
    Charge charge;
    final String blasSecretKey = keyService.getBlasPrivateKey();
    String plainTextCardNumber;
    try {
      charge = paymentsService.charge(chargeRequest);
      stripePaymentTransactionLog.setStripeTransactionId(charge.getId());
      stripePaymentTransactionLog.setAmountCaptured(charge.getAmountCaptured());
      stripePaymentTransactionLog.setAmountRefund(charge.getAmountRefunded());
      stripePaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      stripePaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      stripePaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
      plainTextCardNumber = aesDecrypt(blasSecretKey, card.getCardNumber());
      stripePaymentTransactionLog.setMaskedCardNumber(
          maskCardNumber(plainTextCardNumber));
      stripePaymentTransactionLog.setRefund(charge.getRefunded());
      new Thread(() -> {
        try {
          String cardNumber = aesDecrypt(keyService.getBlasPrivateKey(),
              cardService.getCardInfoByCardId(card.getCardId(), true).getCardNumber());
          sendStripeReceiptEmail(stripePaymentTransactionLog, username, cardNumber, charge);
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException |
                 NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException exception) {
          throw new BadRequestException(MSG_BLAS_APP_FAILURE, exception);
        }
      }).start();
      ChargeResponse response = buildChargeResponse(
          stripePaymentTransactionLog.getPaymentTransactionLogId(), charge, cardId,
          plainTextCardNumber, false, username);
      log.info(response.toString());
      log.debug("Complete transaction");
      return ResponseEntity.ok(response);
    } catch (StripeException exception) {
      stripePaymentTransactionLog.setStripeTransactionId(exception.getStripeError().getCharge());
      stripePaymentTransactionLog.setLogMessage1(exception.toString());
      stripePaymentTransactionLog.setLogMessage2(exception.getMessage());
      stripePaymentTransactionLog.setLogMessage3(exception.getStripeError().toString());
      centralizedLogService.saveLog(serviceName, ERROR, exception.toString(),
          String.valueOf(exception.getCause()),
          stripePaymentTransactionLog.toString(), null, null,
          String.valueOf(new JSONArray(exception.getStackTrace())), isSendEmailAlert);
      throw new PaymentException(MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(),
          exception.getStripeError().getMessage(), exception);
    } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
             InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException exception) {
      stripePaymentTransactionLog.setLogMessage1(exception.toString());
      stripePaymentTransactionLog.setLogMessage2(exception.getMessage());
      centralizedLogService.saveLog(serviceName, ERROR, exception.toString(),
          String.valueOf(exception.getCause()),
          stripePaymentTransactionLog.toString(), null, null,
          String.valueOf(new JSONArray(exception.getStackTrace())), isSendEmailAlert);
      throw new PaymentException(MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(), exception.getMessage(),
          exception);
    } finally {
      log.debug("Complete transaction");
      stripePaymentTransactionLogService.createStripePaymentTransactionLog(
          stripePaymentTransactionLog);
    }
  }
}
