package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FAILURE;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_ID_MDC_KEY;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INACTIVE_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.payload.payment.ChargeResponse;
import com.blas.blascommon.payload.payment.StripeAddedChargeRequest;
import com.blas.blascommon.security.KeyService;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.StripePaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.merchants.StripeService;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class AddedCardChargeController extends ChargeController<StripeAddedChargeRequest> {

  public AddedCardChargeController(AuthUserService authUserService, StripeService stripeService,
      CardService cardService, KeyService keyService, CentralizedLogService centralizedLogService,
      StripePaymentTransactionLogService stripePaymentTransactionLogService,
      EmailQueueService emailQueueService) {
    super(authUserService, stripeService, cardService, keyService, centralizedLogService,
        stripePaymentTransactionLogService, emailQueueService);
  }

  @Override
  @PostMapping(value = "/charge")
  public ResponseEntity<ChargeResponse> charge(@RequestBody StripeAddedChargeRequest chargeRequest)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    return super.charge(chargeRequest);
  }

  @Override
  protected String preprocessCharge(StripeAddedChargeRequest chargeRequest,
      StripePaymentTransactionLog stripePaymentTransactionLog)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    String cardId = chargeRequest.getCardId();
    MDC.put(CARD_ID_MDC_KEY, cardId);

    Card card = cardService.getCardInfoByCardId(cardId, true);
    if (!StringUtils.equals(getUsernameLoggedIn(), card.getAuthUser().getUsername())) {
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
    return aesDecrypt(keyService.getBlasPrivateKey(), card.getCardNumber());
  }
}
