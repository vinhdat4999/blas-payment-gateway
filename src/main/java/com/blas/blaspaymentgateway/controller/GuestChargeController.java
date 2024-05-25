package com.blas.blaspaymentgateway.controller;

import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.payload.payment.ChargeResponse;
import com.blas.blascommon.payload.payment.StripeGuestChargeRequest;
import com.blas.blascommon.security.KeyService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class GuestChargeController extends ChargeController<StripeGuestChargeRequest> {

  public GuestChargeController(AuthUserService authUserService, StripeService stripeService,
      CardService cardService, KeyService keyService, CentralizedLogService centralizedLogService,
      StripePaymentTransactionLogService stripePaymentTransactionLogService,
      EmailQueueService emailQueueService) {
    super(authUserService, stripeService, cardService, keyService, centralizedLogService,
        stripePaymentTransactionLogService, emailQueueService);
  }

  @Override
  @PostMapping(value = "/guest-charge")
  public ResponseEntity<ChargeResponse> charge(
      @RequestBody StripeGuestChargeRequest chargeRequest)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    return super.charge(chargeRequest);
  }

  @Override
  protected String preprocessCharge(StripeGuestChargeRequest chargeRequest,
      StripePaymentTransactionLog stripePaymentTransactionLog) {
    String cardNumber = chargeRequest.getCardRequest().getCardNumber();
    stripePaymentTransactionLog.setMaskedCardNumber(maskCardNumber(cardNumber));
    return cardNumber;
  }
}
