package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.constants.Response.CANNOT_CONNECT_TO_HOST;
import static com.blas.blascommon.enums.EmailTemplate.ADD_CARD_SUCCESS;
import static com.blas.blascommon.enums.LogType.ERROR;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blascommon.utils.httprequest.PostRequest.sendPostRequestWithJsonArrayPayload;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_ADDED_SUCCESSFULLY;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_EXISTED;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_ADD_NEW_CARD_SUCCESSFULLY;
import static com.blas.blaspaymentgateway.utils.PaymentUtils.maskCardNumber;
import static java.time.LocalDateTime.now;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.ServiceUnavailableException;
import com.blas.blascommon.jwt.JwtTokenUtil;
import com.blas.blascommon.payload.CardRequest;
import com.blas.blascommon.payload.CardResponse;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.properties.BlasEmailConfiguration;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.KeyService;
import com.blas.blaspaymentgateway.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Token;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardController {

  @Value("${blas.blas-idp.isSendEmailAlert}")
  private boolean isSendEmailAlert;

  @Value("${blas.service.serviceName}")
  private String serviceName;

  @Lazy
  @Autowired
  private AuthUserService authUserService;

  @Lazy
  @Autowired
  private StripeService stripeService;

  @Lazy
  @Autowired
  private CardService cardService;

  @Lazy
  @Autowired
  private KeyService keyService;

  @Lazy
  @Autowired
  private BlasEmailConfiguration blasEmailConfiguration;

  @Lazy
  @Autowired
  private CentralizedLogService centralizedLogService;

  @Lazy
  @Autowired
  private JwtTokenUtil jwtTokenUtil;

  static void sendEmail(HtmlEmailRequest htmlEmailRequest,
      BlasEmailConfiguration blasEmailConfiguration, JwtTokenUtil jwtTokenUtil,
      CentralizedLogService centralizedLogService, String serviceName, boolean isSendEmailAlert) {
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

  @PostMapping(value = "/add-card")
  public ResponseEntity<CardResponse> charge(@RequestBody CardRequest cardRequest,
      Authentication authentication) {
    try {
      final String blasSecretKey = keyService.getBlasPrivateKey();
      for (Card card : cardService.getAllCards()) {
        if (aesDecrypt(blasSecretKey, card.getCardNumber()).equals(cardRequest.getCardNumber())) {
          throw new BadRequestException(CARD_EXISTED);
        }
      }
      Card card = Card.builder()
          .authUser(authUserService.getAuthUserByUsername(authentication.getName()))
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
      try {
        token = stripeService.getStripeTransactionTokenWithRawCardInfo(card);
      } catch (StripeException exception) {
        throw new BadRequestException(exception.getStripeError().getMessage());
      }
      card.setCardNumber(aesEncrypt(blasSecretKey, card.getCardNumber()));
      card.setCardHolder(aesEncrypt(blasSecretKey, card.getCardHolder()));
      card.setExpMonth(aesEncrypt(blasSecretKey, card.getExpMonth()));
      card.setExpYear(aesEncrypt(blasSecretKey, card.getExpYear()));
      card.setCvc(aesEncrypt(blasSecretKey, card.getCvc()));
      String cardId = cardService.addNewCard(card);
      new Thread(() -> sendEmailAddCardSuccessfully(card.getAuthUser().getUserDetail().getEmail(),
          maskCardNumber(rawCardNumber), token.getCard().getBrand().toUpperCase())).start();
      return ResponseEntity.ok(CardResponse.builder()
          .cardId(cardId)
          .maskedCardNumber(maskCardNumber(rawCardNumber))
          .cardType(token.getCard().getBrand().toUpperCase())
          .addedTime(now())
          .message(CARD_ADDED_SUCCESSFULLY)
          .build());
    } catch (IllegalBlockSizeException | BadPaddingException |
             InvalidAlgorithmParameterException | InvalidKeyException |
             NoSuchPaddingException | NoSuchAlgorithmException exception) {
      throw new BadRequestException(exception);
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
    sendEmail(htmlEmailRequest, blasEmailConfiguration, jwtTokenUtil, centralizedLogService,
        serviceName, isSendEmailAlert);
  }
}
