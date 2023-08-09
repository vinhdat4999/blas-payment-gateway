package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.enums.EmailTemplate.PAYMENT_RECEIPT;
import static com.blas.blascommon.utils.IdUtils.genMixID;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_EMAIL_RECEIPT;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.springframework.http.HttpStatus.OK;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.ChargeResponse;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.properties.BlasEmailConfiguration;
import com.blas.blascommon.utils.StringUtils;
import com.blas.blaspaymentgateway.model.BlasPaymentTransactionLog;
import com.blas.blaspaymentgateway.service.BlasPaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.KeyService;
import com.blas.blaspaymentgateway.service.StripeService;
import com.stripe.model.Charge;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChargeController {

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Value("${blas.service.serviceName}")
  protected String serviceName;

  @Lazy
  @Autowired
  protected AuthUserService authUserService;

  @Lazy
  @Autowired
  protected StripeService stripeService;

  @Lazy
  @Autowired
  protected CardService cardService;

  @Lazy
  @Autowired
  protected KeyService keyService;

  @Lazy
  @Autowired
  protected BlasEmailConfiguration blasEmailConfiguration;

  @Lazy
  @Autowired
  protected CentralizedLogService centralizedLogService;

  @Lazy
  @Autowired
  protected JwtTokenUtil jwtTokenUtil;

  @Value("${blas.blas-payment-gateway.lengthOfId}")
  protected int lengthOfId;

  @Lazy
  @Autowired
  protected StripeService paymentsService;

  @Lazy
  @Autowired
  protected BlasPaymentTransactionLogService blasPaymentTransactionLogService;

  protected static String genTransactionId(
      BlasPaymentTransactionLogService blasPaymentTransactionLogService, int lengthOfId) {
    String transactionId;
    do {
      transactionId = genMixID(lengthOfId).toUpperCase();
    } while (blasPaymentTransactionLogService.isExistedId(transactionId));
    return transactionId;
  }

  protected static ChargeResponse buildChargeResponse(String transactionId, Charge charge,
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

  protected static String getReformattedAmount(Long amount, String currency) {
    return (double) (amount) / 100 + StringUtils.SPACE + currency.toUpperCase();
  }

  protected void sendReceiptEmail(BlasPaymentTransactionLog blasPaymentTransaction, String username,
      String cardNumber, Charge charge) {
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
