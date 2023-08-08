package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.enums.EmailTemplate.PAYMENT_RECEIPT;
import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blascommon.utils.StringUtils.SPACE;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INACTIVE_EXISTED_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_EMAIL_RECEIPT;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.buildChargeResponse;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.genTransactionId;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.ChargeResponse;
import com.blas.blascommon.payload.GuestChargeRequest;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.properties.BlasEmailConfiguration;
import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.service.BlasPaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.KeyService;
import com.blas.blaspaymentgateway.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class GuestChargeController {

  public static final String EXISTED_CARD_MESSAGE = "You already added this card to your account before";
  @Value("${blas.blas-idp.isSendEmailAlert}")
  private boolean isSendEmailAlert;

  @Value("${blas.service.serviceName}")
  private String serviceName;

  @Value("${blas.blas-payment-gateway.lengthOfId}")
  private int lengthOfId;

  @Lazy
  @Autowired
  private StripeService paymentsService;

  @Lazy
  @Autowired
  private CardService cardService;

  @Lazy
  @Autowired
  private KeyService keyService;

  @Lazy
  @Autowired
  private AuthUserService authUserService;

  @Lazy
  @Autowired
  private BlasPaymentTransactionLogService blasPaymentTransactionLogService;

  @Lazy
  @Autowired
  private BlasEmailConfiguration blasEmailConfiguration;

  @Lazy
  @Autowired
  private CentralizedLogService centralizedLogService;

  @Lazy
  @Autowired
  private JwtTokenUtil jwtTokenUtil;

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
        try {
          sendReceiptEmail(blasPaymentTransactionLog, username, cardNumber, charge);
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException |
                 NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException exception) {
          throw new BadRequestException(exception);
        }
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

  private void sendReceiptEmail(BlasPaymentTransactionLog blasPaymentTransaction, String username,
      String cardNumber, Charge charge)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    AuthUser authUser = authUserService.getAuthUserByUsername(username);
    HtmlEmailRequest htmlEmailRequest = new HtmlEmailRequest();
    htmlEmailRequest.setEmailTo(authUser.getUserDetail().getEmail());
    htmlEmailRequest.setTitle(SUBJECT_EMAIL_RECEIPT);
    htmlEmailRequest.setEmailTemplateName(PAYMENT_RECEIPT.name());
    htmlEmailRequest.setData(Map.ofEntries(
        Map.entry("email", authUser.getUserDetail().getEmail()),
        Map.entry("phone", authUser.getUserDetail().getPhoneNumber()),
        Map.entry("name", authUser.getUserDetail().getFirstName() + SPACE + authUser.getUserDetail()
            .getLastName()),
        Map.entry("transactionId", blasPaymentTransaction.getPaymentTransactionLogId()),
        Map.entry("transactionTime", blasPaymentTransaction.getTransactionTime().toString()),
        Map.entry("cardType", blasPaymentTransaction.getCardType()),
        Map.entry("cardNumber", maskCardNumber(cardNumber)),
        Map.entry("status", charge.getStatus().toUpperCase()),
        Map.entry("description", charge.getDescription()),
        Map.entry("amount", String.valueOf((double) (charge.getAmountCaptured()) / 100)),
        Map.entry("currency", charge.getCurrency().toUpperCase())
    ));
    CardController.sendEmail(htmlEmailRequest, blasEmailConfiguration, jwtTokenUtil,
        centralizedLogService, serviceName, isSendEmailAlert);
  }
}
