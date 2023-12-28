package com.blas.blaspaymentgateway.service.merchants;

import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.STRIPE_PRIVATE_KEY;

import com.blas.blascommon.core.service.BlasConfigService;
import com.blas.blascommon.payload.payment.CardRequest;
import com.blas.blascommon.payload.payment.StripeChargeRequest;
import com.blas.blascommon.payload.payment.StripeGuestChargeRequest;
import com.blas.blascommon.security.KeyService;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.service.CardService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Token;
import jakarta.annotation.PostConstruct;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StripeService {

  @Lazy
  private final KeyService keyService;

  @Lazy
  private final BlasConfigService blasConfigService;

  @Lazy
  private final CardService cardService;

  @PostConstruct
  public void init()
      throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, InvalidKeyException {
    final String hashedSecretKey = blasConfigService.getConfigValueFromKey(STRIPE_PRIVATE_KEY);
    final String blasSecretKey = keyService.getBlasPrivateKey();
    Stripe.apiKey = aesDecrypt(blasSecretKey, hashedSecretKey);
  }

  public Charge charge(final StripeChargeRequest chargeRequest)
      throws StripeException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    final Card card = cardService.getCardInfoByCardId(chargeRequest.getCardId(), true);
    final Map<String, Object> chargeParams = Map.ofEntries(
        Map.entry("amount", chargeRequest.getAmount()),
        Map.entry("currency", chargeRequest.getCurrency()),
        Map.entry("description", chargeRequest.getDescription()),
        Map.entry("source", this.getStripeTransactionToken(card).getId()));
    return Charge.create(chargeParams);
  }

  public Charge charge(final StripeGuestChargeRequest guestChargeRequest)
      throws StripeException {
    final CardRequest cardRequest = guestChargeRequest.getCardRequest();
    final Card card = Card.builder()
        .cardNumber(cardRequest.getCardNumber())
        .cardHolder(cardRequest.getCardHolder())
        .expMonth(cardRequest.getExpMonth())
        .expYear(cardRequest.getExpYear())
        .cvc(cardRequest.getCvc())
        .build();
    final Map<String, Object> chargeParams = Map.ofEntries(
        Map.entry("amount", guestChargeRequest.getAmount()),
        Map.entry("currency", guestChargeRequest.getCurrency()),
        Map.entry("description", guestChargeRequest.getDescription()),
        Map.entry("source", this.getStripeTransactionTokenWithRawCardInfo(card).getId()));
    return Charge.create(chargeParams);
  }

  public Token getStripeTransactionToken(Card card)
      throws StripeException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    final String blasSecretKey = keyService.getBlasPrivateKey();
    final Map<String, Object> cardMap = Map.ofEntries(
        Map.entry("number", aesDecrypt(blasSecretKey, card.getCardNumber())),
        Map.entry("exp_month", Integer.parseInt(aesDecrypt(blasSecretKey, card.getExpMonth()))),
        Map.entry("exp_year", Integer.parseInt(aesDecrypt(blasSecretKey, card.getExpYear()))),
        Map.entry("cvc", aesDecrypt(blasSecretKey, card.getCvc())));
    Map<String, Object> params = Map.of("card", cardMap);
    return Token.create(params);
  }

  public Token getStripeTransactionTokenWithRawCardInfo(Card card)
      throws StripeException {
    final Map<String, Object> cardMap = Map.ofEntries(
        Map.entry("number", card.getCardNumber()),
        Map.entry("exp_month", Integer.parseInt(card.getExpMonth())),
        Map.entry("exp_year", Integer.parseInt(card.getExpYear())),
        Map.entry("cvc", card.getCvc()));
    final Map<String, Object> params = Map.of("card", cardMap);
    return Token.create(params);
  }
}
