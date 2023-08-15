package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INACTIVE_EXISTED_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.payload.ChargeResponse;
import com.blas.blascommon.payload.GuestChargeRequest;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GuestChargeController extends ChargeController {

  public static final String EXISTED_CARD_MESSAGE = "You already added this card to your account before";

  @PostMapping(value = "/guest-charge")
  public ResponseEntity<ChargeResponse> guestCharge(
      @RequestBody GuestChargeRequest guestChargeRequest, Authentication authentication)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    String username = authentication.getName();
    BlasPaymentTransactionLog blasPaymentTransactionLog = BlasPaymentTransactionLog.builder()
        .paymentTransactionLogId(genTransactionId(blasPaymentTransactionLogService, lengthOfId))
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(username))
        .currency(guestChargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(guestChargeRequest.getDescription())
        .isGuestCard(true)
        .build();
    String cardNumber = guestChargeRequest.getCardRequest().getCardNumber();
    final String blasSecretKey = keyService.getBlasPrivateKey();
    Card card = cardService.getCardInfoByCardNumber(aesEncrypt(blasSecretKey, cardNumber));
    if (card != null && card.getAuthUser().getUsername().equals(username)) {
      if (!card.isActive()) {
        throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
            INACTIVE_EXISTED_CARD);
      }
      blasPaymentTransactionLog.setCard(card);
      blasPaymentTransactionLog.setNote(EXISTED_CARD_MESSAGE);
      blasPaymentTransactionLog.setMaskedCardNumber(maskCardNumber(card.getCardNumber()));
    } else {
      blasPaymentTransactionLog.setMaskedCardNumber(maskCardNumber(cardNumber));
    }
    Charge charge;
    try {
      charge = paymentsService.charge(guestChargeRequest);
      blasPaymentTransactionLog.setStripeTransactionId(charge.getId());
      blasPaymentTransactionLog.setAmountCaptured(charge.getAmountCaptured());
      blasPaymentTransactionLog.setAmountRefund(charge.getAmountRefunded());
      blasPaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      blasPaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      blasPaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
      blasPaymentTransactionLog.setRefund(charge.getRefunded());
      new Thread(() -> {
        sendReceiptEmail(blasPaymentTransactionLog, username, cardNumber, charge);
      }).start();
      return ResponseEntity.ok(
          buildChargeResponse(blasPaymentTransactionLog.getPaymentTransactionLogId(), charge, null,
              cardNumber, true, username));
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
