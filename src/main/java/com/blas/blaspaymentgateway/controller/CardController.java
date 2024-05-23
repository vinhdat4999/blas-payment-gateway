package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.constants.MDCConstant.GLOBAL_ID;
import static com.blas.blascommon.enums.EmailTemplate.ADD_CARD_SUCCESS;
import static com.blas.blascommon.exceptions.BlasErrorCodeEnum.MSG_BLAS_APP_FAILURE;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_ADDED_SUCCESSFULLY;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_EXISTED;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_ADD_NEW_CARD_SUCCESSFULLY;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;
import static org.springframework.http.HttpStatus.OK;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.payment.CardRequest;
import com.blas.blascommon.payload.payment.CardResponse;
import com.blas.blascommon.security.KeyService;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.merchants.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Token;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CardController {

  @Lazy
  private final AuthUserService authUserService;

  @Lazy
  private final StripeService stripeService;

  @Lazy
  private final CardService cardService;

  @Lazy
  private final KeyService keyService;

  @Lazy
  private final EmailQueueService emailQueueService;

  @PostMapping(value = "/add-card")
  public ResponseEntity<CardResponse> charge(@RequestBody CardRequest cardRequest,
      Authentication authentication) {
    try {
      String username = authentication.getName();
      final String blasSecretKey = keyService.getBlasPrivateKey();
      for (Card card : cardService.getAllCards()) {
        if (aesDecrypt(blasSecretKey, card.getCardNumber()).equals(cardRequest.getCardNumber())
            && StringUtils.equals(username, card.getAuthUser().getUsername())) {
          throw new BadRequestException(CARD_EXISTED);
        }
      }
      Card card = Card.builder()
          .authUser(authUserService.getAuthUserByUsername(username))
          .cardNumber(cardRequest.getCardNumber())
          .cardHolder(cardRequest.getCardHolder())
          .expMonth(cardRequest.getExpMonth())
          .expYear(cardRequest.getExpYear())
          .cvc(cardRequest.getCvc())
          .addedTime(now())
          .isActive(true)
          .build();
      final String rawCardNumber = card.getCardNumber();
      Token token;
      token = stripeService.getStripeTransactionTokenWithRawCardInfo(card);
      card.setCardNumber(aesEncrypt(blasSecretKey, card.getCardNumber()));
      card.setCardHolder(aesEncrypt(blasSecretKey, card.getCardHolder()));
      card.setExpMonth(aesEncrypt(blasSecretKey, card.getExpMonth()));
      card.setExpYear(aesEncrypt(blasSecretKey, card.getExpYear()));
      card.setCvc(aesEncrypt(blasSecretKey, card.getCvc()));
      String cardId = cardService.addNewCard(card);
      new Thread(() -> sendEmailAddCardSuccessfully(card.getAuthUser().getUserDetail().getEmail(),
          maskCardNumber(rawCardNumber), token.getCard().getBrand().toUpperCase())).start();
      CardResponse response = CardResponse.builder()
          .statusCode(String.valueOf(OK.value()))
          .cardId(cardId)
          .maskedCardNumber(maskCardNumber(rawCardNumber))
          .cardType(token.getCard().getBrand().toUpperCase())
          .addedTime(now())
          .message(CARD_ADDED_SUCCESSFULLY)
          .build();
      log.info("GlobalId: {} - New card added. {}", MDC.get(GLOBAL_ID), response.toString());
      return ResponseEntity.ok(response);
    } catch (IllegalBlockSizeException | BadPaddingException |
             InvalidAlgorithmParameterException | InvalidKeyException |
             NoSuchPaddingException | NoSuchAlgorithmException exception) {
      throw new BadRequestException(MSG_BLAS_APP_FAILURE, exception);
    } catch (StripeException stripeException) {
      throw new BadRequestException(stripeException.getStripeError().getMessage(), stripeException);
    }
  }

  private void sendEmailAddCardSuccessfully(String emailTo, String maskedCardNumber, String brand) {
    HtmlEmailRequest htmlEmailRequest = new HtmlEmailRequest();
    htmlEmailRequest.setEmailTo(emailTo);
    htmlEmailRequest.setTitle(SUBJECT_ADD_NEW_CARD_SUCCESSFULLY);
    htmlEmailRequest.setEmailTemplateName(ADD_CARD_SUCCESS.name());
    htmlEmailRequest.setData(Map.ofEntries(
        Map.entry("cardNumber", maskedCardNumber),
        Map.entry("brand", brand)
    ));
    emailQueueService.sendMessage(new JSONArray(List.of(htmlEmailRequest)).toString());
  }
}
