package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.constants.Response.CANNOT_CONNECT_TO_HOST;
import static com.blas.blascommon.enums.EmailTemplate.PAYMENT_RECEIPT;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.StringUtils.SPACE;
import static com.blas.blascommon.utils.httprequest.PostRequest.sendPostRequestWithJsonArrayPayload;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_ID_SPACE_LABEL;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_EMAIL_RECEIPT;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.CardUtils.maskCardNumber;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.exceptions.types.ServiceUnavailableException;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.ChargeRequest;
import com.blas.blascommon.payload.ChargeResponse;
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
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class ChargeController {

  @Value("${blas.blas-idp.isSendEmailAlert}")
  private boolean isSendEmailAlert;

  @Value("${blas.service.serviceName}")
  private String serviceName;

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
  private JwtTokenUtil jwtTokenUtil;

  @Lazy
  @Autowired
  private CentralizedLogService centralizedLogService;

  @PostMapping(value = "/charge")
  public ResponseEntity<ChargeResponse> charge(@RequestBody ChargeRequest chargeRequest) {
    BlasPaymentTransactionLog blasPaymentTransactionLog = BlasPaymentTransactionLog.builder()
        .paymentTransactionLogId(genUUID())
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(getUsernameLoggedIn()))
        .amount(chargeRequest.getAmount())
        .currency(chargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(chargeRequest.getDescription())
        .build();
    String cardId = chargeRequest.getCardId();
    if (!getUsernameLoggedIn().equals(
        cardService.getCardInfoByCardId(cardId).getAuthUser().getUsername())) {
      blasPaymentTransactionLog.setLogMessage1(TRANSACTION_FAILED);
      blasPaymentTransactionLog.setLogMessage2(CARD_ID_SPACE_LABEL + cardId);
      blasPaymentTransactionLogService.createBlasPaymentTransactionLog(blasPaymentTransactionLog);
      throw new PaymentException(blasPaymentTransactionLog.getPaymentTransactionLogId(),
          INVALID_CARD);
    }
    blasPaymentTransactionLog.setCard(cardService.getCardInfoByCardId(cardId));
    Charge charge;
    try {
      charge = paymentsService.charge(chargeRequest);
      blasPaymentTransactionLog.setStripeTransactionId(charge.getId());
      blasPaymentTransactionLog.setAmount(charge.getAmountCaptured());
      blasPaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      blasPaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      blasPaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
      new Thread(() -> {
        try {
          sendReceiptEmail(blasPaymentTransactionLog,
              cardService.getCardInfoByCardId(cardId), charge);
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException |
                 NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException exception) {
          throw new BadRequestException(exception);
        }
      }).start();
      return ResponseEntity.ok(
          buildChargeResponse(blasPaymentTransactionLog.getPaymentTransactionLogId(), charge,
              cardId, getUsernameLoggedIn()));
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

  private void sendReceiptEmail(BlasPaymentTransactionLog blasPaymentTransaction, Card card,
      Charge charge)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    AuthUser authUser = authUserService.getAuthUserByUserId(card.getAuthUser().getUserId());
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
        Map.entry("cardNumber", maskCardNumber(aesDecrypt(keyService.getBlasPrivateKey(),
            cardService.getCardInfoByCardId(card.getCardId()).getCardNumber()))),
        Map.entry("status", charge.getStatus().toUpperCase()),
        Map.entry("description", charge.getDescription()),
        Map.entry("amount", String.valueOf((double) (charge.getAmountCaptured()) / 100)),
        Map.entry("currency", charge.getCurrency().toUpperCase())
    ));
    try {
      sendPostRequestWithJsonArrayPayload(blasEmailConfiguration.getEndpointHtmlEmail(), null,
          jwtTokenUtil.generateInternalSystemToken(), new JSONArray(List.of(htmlEmailRequest)));
    } catch (IOException | JSONException e) {
      centralizedLogService.saveLog(serviceName, ERROR, e.toString(),
          e.getCause() == null ? EMPTY : e.getCause().toString(),
          new JSONArray(List.of(htmlEmailRequest)).toString(), null, null,
          String.valueOf(new JSONArray(e.getStackTrace())), isSendEmailAlert);
      throw new ServiceUnavailableException(CANNOT_CONNECT_TO_HOST);
    }
  }

  private ChargeResponse buildChargeResponse(String transactionId, Charge charge, String cardId,
      String username)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    return ChargeResponse.builder()
        .transactionId(transactionId)
        .transactionTime(
            LocalDateTime.ofEpochSecond(charge.getCreated(), 0, ZoneOffset.UTC).minusHours(-7))
        .cardId(cardId)
        .maskedCardNumber(maskCardNumber(aesDecrypt(keyService.getBlasPrivateKey(),
            cardService.getCardInfoByCardId(cardId).getCardNumber())))
        .cardType(charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase())
        .username(username)
        .amountCaptured(
            (double) (charge.getAmountCaptured()) / 100 + SPACE + charge.getCurrency()
                .toUpperCase())
        .status(charge.getStatus().toUpperCase())
        .description(charge.getDescription())
        .build();
  }
}
