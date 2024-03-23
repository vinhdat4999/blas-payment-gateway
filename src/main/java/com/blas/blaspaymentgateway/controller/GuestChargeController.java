package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INACTIVE_EXISTED_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.BlasErrorCodeEnum;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.payment.ChargeResponse;
import com.blas.blascommon.payload.payment.StripeGuestChargeRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class GuestChargeController extends ChargeController {

  public static final String EXISTED_CARD_MESSAGE = "You already added this card to your account before";

  public GuestChargeController(AuthUserService authUserService, StripeService stripeService,
      CardService cardService, KeyService keyService, CentralizedLogService centralizedLogService,
      JwtTokenUtil jwtTokenUtil, StripeService paymentsService,
      StripePaymentTransactionLogService stripePaymentTransactionLogService,
      EmailQueueService emailQueueService) {
    super(authUserService, stripeService, cardService, keyService, centralizedLogService,
        jwtTokenUtil, paymentsService, stripePaymentTransactionLogService, emailQueueService);
  }

  @PostMapping(value = "/guest-charge")
  public ResponseEntity<ChargeResponse> guestCharge(
      @RequestBody StripeGuestChargeRequest guestChargeRequest, Authentication authentication)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    log.debug("Start transaction...");
    String username = authentication.getName();
    StripePaymentTransactionLog stripePaymentTransactionLog = StripePaymentTransactionLog.builder()
        .paymentTransactionLogId(genTransactionId(stripePaymentTransactionLogService, lengthOfId))
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(username))
        .currency(guestChargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(guestChargeRequest.getDescription())
        .isGuestCard(true)
        .build();
    log.info(
        "blasPaymentTransactionLogId: " + stripePaymentTransactionLog.getPaymentTransactionLogId());
    String cardNumber = guestChargeRequest.getCardRequest().getCardNumber();
    final String blasSecretKey = keyService.getBlasPrivateKey();
    Card card = cardService.getCardInfoByCardNumber(aesEncrypt(blasSecretKey, cardNumber));
    if (card != null && card.getAuthUser().getUsername().equals(username)) {
      if (!card.isActive()) {
        throw new PaymentException(BlasErrorCodeEnum.MSG_FAILURE,
            stripePaymentTransactionLog.getPaymentTransactionLogId(), INACTIVE_EXISTED_CARD);
      }
      stripePaymentTransactionLog.setCard(card);
      stripePaymentTransactionLog.setNote(EXISTED_CARD_MESSAGE);
      stripePaymentTransactionLog.setMaskedCardNumber(maskCardNumber(card.getCardNumber()));
    } else {
      stripePaymentTransactionLog.setMaskedCardNumber(maskCardNumber(cardNumber));
    }
    Charge charge;
    try {
      charge = paymentsService.charge(guestChargeRequest);
      stripePaymentTransactionLog.setStripeTransactionId(charge.getId());
      stripePaymentTransactionLog.setAmountCaptured(charge.getAmountCaptured());
      stripePaymentTransactionLog.setAmountRefund(charge.getAmountRefunded());
      stripePaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      stripePaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      stripePaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
      stripePaymentTransactionLog.setRefund(charge.getRefunded());
      new Thread(
          () -> sendStripeReceiptEmail(stripePaymentTransactionLog, username, cardNumber,
              charge)).start();
      ChargeResponse response = buildChargeResponse(
          stripePaymentTransactionLog.getPaymentTransactionLogId(), charge, null,
          cardNumber, true, username);
      log.info(response.toString());
      log.debug("Complete transaction");
      return ResponseEntity.ok(response);
    } catch (StripeException exception) {
      stripePaymentTransactionLog.setStripeTransactionId(exception.getStripeError().getCharge());
      stripePaymentTransactionLog.setLogMessage1(exception.toString());
      stripePaymentTransactionLog.setLogMessage2(exception.getMessage());
      stripePaymentTransactionLog.setLogMessage3(exception.getStripeError().toString());
      centralizedLogService.saveLog(exception, stripePaymentTransactionLog, null, null);
      throw new PaymentException(BlasErrorCodeEnum.MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(),
          exception.getStripeError().getMessage(), exception);
    } finally {
      log.debug("Complete transaction");
      stripePaymentTransactionLogService.createStripePaymentTransactionLog(
          stripePaymentTransactionLog);
    }
  }
}
