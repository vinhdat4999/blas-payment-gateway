package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.constants.MdcConstants.GLOBAL_ID;
import static com.blas.blascommon.enums.EmailTemplate.STRIPE_PAYMENT_RECEIPT;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_FAILURE;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.utils.idutils.IdUtils.genMixID;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_ID_MDC_KEY;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_EMAIL_RECEIPT;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.TRANSACTION_FAILED;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.springframework.http.HttpStatus.OK;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.UserDetail;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.PaymentException;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.payment.ChargeResponse;
import com.blas.blascommon.payload.payment.StripeAddedChargeRequest;
import com.blas.blascommon.payload.payment.StripeChargeRequest;
import com.blas.blascommon.payload.payment.StripeGuestChargeRequest;
import com.blas.blascommon.security.KeyService;
import com.blas.blascommon.utils.StringUtils;
import com.blas.blaspaymentgateway.model.StripePaymentTransactionLog;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.StripePaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.merchants.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public abstract class ChargeController<T extends StripeChargeRequest> {

  private static final String INVALID_PAYMENT_REQUEST = "Invalid payment request";

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
  protected final StripePaymentTransactionLogService stripePaymentTransactionLogService;

  @Lazy
  private final EmailQueueService emailQueueService;

  @Value("${blas.blas-payment-gateway.lengthOfId}")
  protected int lengthOfId;

  /**
   * Check blocked card and return card number
   *
   * @param chargeRequest
   * @param stripePaymentTransactionLog
   * @return cardNumber - Card number
   */
  protected abstract String preprocessCharge(T chargeRequest,
      StripePaymentTransactionLog stripePaymentTransactionLog)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException;

  protected ResponseEntity<ChargeResponse> charge(T chargeRequest)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    log.debug("Start transaction...");
    String username = getUsernameLoggedIn();
    StripePaymentTransactionLog stripePaymentTransactionLog = StripePaymentTransactionLog.builder()
        .paymentTransactionLogId(genTransactionId(stripePaymentTransactionLogService, lengthOfId))
        .globalId(MDC.get(GLOBAL_ID))
        .transactionTime(now())
        .authUser(authUserService.getAuthUserByUsername(username))
        .currency(chargeRequest.getCurrency().name())
        .status(TRANSACTION_FAILED)
        .description(chargeRequest.getDescription())
        .isGuestCard(chargeRequest instanceof StripeGuestChargeRequest)
        .build();

    log.info(
        "blasPaymentTransactionLogId: " + stripePaymentTransactionLog.getPaymentTransactionLogId());

    String plainTextCardNumber = preprocessCharge(chargeRequest, stripePaymentTransactionLog);

    Charge charge = null;
    try {
      if (chargeRequest instanceof StripeAddedChargeRequest stripeAddedchargerequest) {
        charge = stripeService.addedCardCharge(stripeAddedchargerequest);
      } else if (chargeRequest instanceof StripeGuestChargeRequest stripeGuestChargeRequest) {
        charge = stripeService.guestCardCharge(stripeGuestChargeRequest);
      }

      if (charge == null) {
        throw new PaymentException(MSG_FAILURE,
            stripePaymentTransactionLog.getPaymentTransactionLogId(), INVALID_PAYMENT_REQUEST);
      }

      stripePaymentTransactionLog.setStripeTransactionId(charge.getId());
      stripePaymentTransactionLog.setAmountCaptured(charge.getAmountCaptured());
      stripePaymentTransactionLog.setAmountRefund(charge.getAmountRefunded());
      stripePaymentTransactionLog.setReceiptUrl(charge.getReceiptUrl());
      stripePaymentTransactionLog.setStatus(charge.getStatus().toUpperCase());
      stripePaymentTransactionLog.setCardType(
          charge.getPaymentMethodDetails().getCard().getBrand().toUpperCase());
      stripePaymentTransactionLog.setMaskedCardNumber(
          maskCardNumber(plainTextCardNumber));
      stripePaymentTransactionLog.setRefund(charge.getRefunded());

      Charge finalCharge = charge;
      new Thread(
          () -> sendStripeReceiptEmail(stripePaymentTransactionLog, username, plainTextCardNumber,
              finalCharge)).start();

      ChargeResponse response = buildChargeResponse(
          stripePaymentTransactionLog.getPaymentTransactionLogId(), charge,
          MDC.get(CARD_ID_MDC_KEY), plainTextCardNumber,
          chargeRequest instanceof StripeGuestChargeRequest, username);
      MDC.remove(CARD_ID_MDC_KEY);
      log.info(response.toString());

      return ResponseEntity.ok(response);
    } catch (StripeException exception) {
      stripePaymentTransactionLog.setStripeTransactionId(exception.getStripeError().getCharge());
      stripePaymentTransactionLog.setLogMessage1(exception.toString());
      stripePaymentTransactionLog.setLogMessage2(exception.getMessage());
      stripePaymentTransactionLog.setLogMessage3(exception.getStripeError().toString());
      centralizedLogService.saveLog(exception, stripePaymentTransactionLog, null, null);

      throw new PaymentException(MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(),
          exception.getStripeError().getMessage(), exception);
    } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
             InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException exception) {
      stripePaymentTransactionLog.setLogMessage1(exception.toString());
      stripePaymentTransactionLog.setLogMessage2(exception.getMessage());
      centralizedLogService.saveLog(exception, stripePaymentTransactionLog, null, null);

      throw new PaymentException(MSG_FAILURE,
          stripePaymentTransactionLog.getPaymentTransactionLogId(), exception.getMessage(),
          exception);
    } finally {
      log.debug("Complete transaction");
      stripePaymentTransactionLogService.createStripePaymentTransactionLog(
          stripePaymentTransactionLog);
    }
  }

  private String genTransactionId(
      StripePaymentTransactionLogService stripePaymentTransactionLogService, int lengthOfId) {
    String transactionId;
    do {
      transactionId = genMixID(lengthOfId).toUpperCase();
    } while (stripePaymentTransactionLogService.isExistedId(transactionId));
    return transactionId;
  }

  private ChargeResponse buildChargeResponse(String transactionId, Charge charge,
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

  private String getReformattedAmount(Long amount, String currency) {
    return (double) (amount) / 100 + StringUtils.SPACE + currency.toUpperCase();
  }

  private void sendStripeReceiptEmail(StripePaymentTransactionLog blasPaymentTransaction,
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
