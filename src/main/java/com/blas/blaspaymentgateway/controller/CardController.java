package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_EXISTED;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.payload.CardRequest;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.KeyService;
import com.blas.blaspaymentgateway.service.StripeService;
import com.stripe.exception.StripeException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardController {

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

  @PostMapping(value = "/add-card")
  public ResponseEntity<String> charge(@RequestBody CardRequest cardRequest,
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
          .isActive(true)
          .build();
      try {
        stripeService.getStripeTransactionToken(card);
      } catch (StripeException exception) {
        throw new BadRequestException(exception.getMessage());
      }
      card.setCardNumber(aesEncrypt(blasSecretKey, card.getCardNumber()));
      card.setCardHolder(aesEncrypt(blasSecretKey, card.getCardHolder()));
      card.setExpMonth(aesEncrypt(blasSecretKey, card.getExpMonth()));
      card.setExpYear(aesEncrypt(blasSecretKey, card.getExpYear()));
      card.setCvc(aesEncrypt(blasSecretKey, card.getCvc()));
      String cardId = cardService.addNewCard(card);
      return ResponseEntity.ok("Card successfully added. Card ID: " + cardId);
    } catch (IllegalBlockSizeException | BadPaddingException |
             InvalidAlgorithmParameterException | InvalidKeyException |
             NoSuchPaddingException | NoSuchAlgorithmException exception) {
      throw new BadRequestException(exception);
    }
  }
}
