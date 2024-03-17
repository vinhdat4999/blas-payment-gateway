package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.enums.EmailTemplate.STRIPE_PAYMENT_RECEIPT;
import static com.blas.blascommon.utils.IdUtils.genMixID;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_EMAIL_RECEIPT;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.springframework.http.HttpStatus.OK;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.UserDetail;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.payment.ChargeResponse;
import com.blas.blascommon.security.KeyService;
import com.blas.blascommon.utils.StringUtils;
import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.StripePaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.merchants.StripeService;
import com.stripe.model.Charge;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChargeController {

  @Lazy
  protected final AuthUserService authUserService;

  @Lazy
  protected final StripeService stripeService;

  @Lazy
  protected final CardService cardService;

  @Lazy
  protected final KeyService keyService;

  @Lazy
  protected final CentralizedLogService centralizedLogService;

  @Lazy
  protected final JwtTokenUtil jwtTokenUtil;

  @Lazy
  protected final StripeService paymentsService;

  @Lazy
  protected final StripePaymentTransactionLogService stripePaymentTransactionLogService;

  @Lazy
  private final EmailQueueService emailQueueService;

  @Value("${blas.blas-idp.isSendEmailAlert}")
  protected boolean isSendEmailAlert;

  @Value("${blas.service.serviceName}")
  protected String serviceName;

  @Value("${blas.blas-payment-gateway.lengthOfId}")
  protected int lengthOfId;

  protected static String genTransactionId(
      StripePaymentTransactionLogService stripePaymentTransactionLogService, int lengthOfId) {
    String transactionId;
    do {
      transactionId = genMixID(lengthOfId).toUpperCase();
    } while (stripePaymentTransactionLogService.isExistedId(transactionId));
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

  protected void sendStripeReceiptEmail(StripePaymentTransactionLog blasPaymentTransaction,
      String username,
      String cardNumber, Charge charge) {
    AuthUser authUser = authUserService.getAuthUserByUsername(username);
    HtmlEmailRequest htmlEmailRequest = new HtmlEmailRequest();
    UserDetail userDetail = authUser.getUserDetail();
    htmlEmailRequest.setEmailTo(userDetail.getEmail());
    htmlEmailRequest.setTitle(SUBJECT_EMAIL_RECEIPT);
    htmlEmailRequest.setEmailTemplateName(STRIPE_PAYMENT_RECEIPT.name());
    htmlEmailRequest.setData(Map.ofEntries(
        Map.entry("email", userDetail.getEmail()),
        Map.entry("phone", userDetail.getPhoneNumber()),
        Map.entry("name", userDetail.getFirstName() + SPACE + userDetail
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
    emailQueueService.sendMessage(new JSONArray(List.of(htmlEmailRequest)).toString());
  }
}
