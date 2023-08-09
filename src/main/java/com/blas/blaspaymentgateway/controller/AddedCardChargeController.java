package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INACTIVE_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.payload.ChargeRequest;
import com.blas.blascommon.payload.ChargeResponse;
import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;
import com.blas.blaspaymentgateway.model.Card;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AddedCardChargeController extends ChargeController {

  @PostMapping(value = "/charge")
  public ResponseEntity<ChargeResponse> charge(@RequestBody ChargeRequest chargeRequest) {
    String username = getUsernameLoggedIn();
    BlasPaymentTransactionLog blasPaymentTransactionLog = BlasPaymentTransactionLog.builder()
        .paymentTransactionLogId(genTransactionId(blasPaymentTransactionLogService, lengthOfId))
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(username))
        .currency(chargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(chargeRequest.getDescription())
        .build();
    String cardId = chargeRequest.getCardId();
    Card card = cardService.getCardInfoByCardId(cardId, true);
    if (!username.equals(card.getAuthUser().getUsername())) {
      blasPaymentTransactionLog.setLogMessage1(INVALID_CARD);
      blasPaymentTransactionLog.setCard(card);
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          INVALID_CARD);
    }
    if (!card.isActive()) {
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          INACTIVE_CARD);
    }
    blasPaymentTransactionLog.setCard(card);
    Charge charge;
    final String blasSecretKey = keyService.getBlasPrivateKey();
    String plainTextCardNumber;
    try {
      charge = paymentsService.charge(chargeRequest);
      blasPaymentTransactionLog.setStripeTransactionId(charge.getId());
      blasPaymentTransactionLog.setAmountCaptured(charge.getAmountCaptured());
      blasPaymentTransactionLog.setAmountRefund(charge.getAmountRefunded());
      blasPaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      blasPaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      blasPaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
      plainTextCardNumber = aesDecrypt(blasSecretKey, card.getCardNumber());
      blasPaymentTransactionLog.setMaskedCardNumber(
          maskCardNumber(plainTextCardNumber));
      blasPaymentTransactionLog.setRefund(charge.getRefunded());
      new Thread(() -> {
        try {
          String cardNumber = aesDecrypt(keyService.getBlasPrivateKey(),
              cardService.getCardInfoByCardId(card.getCardId(), true).getCardNumber());
          sendReceiptEmail(blasPaymentTransactionLog, username, cardNumber, charge);
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException |
                 NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException exception) {
          throw new BadRequestException(exception);
        }
      }).start();
      return ResponseEntity.ok(
          buildChargeResponse(blasPaymentTransactionLog.getPaymentTransactionLogId(), charge,
              cardId, plainTextCardNumber, false, username));
    } catch (StripeException exception) {
      blasPaymentTransactionLog.setStripeTransactionId(exception.getStripeError().getCharge());
      blasPaymentTransactionLog.setLogMessage1(exception.toString());
      blasPaymentTransactionLog.setLogMessage2(exception.getMessage());
      blasPaymentTransactionLog.setLogMessage3(exception.getStripeError().toString());
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          exception.getStripeError().getMessage());
    } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
             InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException exception) {
      blasPaymentTransactionLog.setLogMessage1(exception.toString());
      blasPaymentTransactionLog.setLogMessage2(exception.getMessage());
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          exception.getMessage());
    } finally {
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
    }
  }
}
