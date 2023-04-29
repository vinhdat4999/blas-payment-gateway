package com.blas.blaspaymentgateway.service;

import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.STRIPE_PRIVATE_KEY;

import com.blas.blascommon.core.service.BlasConfigService;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.payload.ChargeRequest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class StripeService {

  @Lazy
  @Autowired
  private KeyService keyService;

  @Lazy
  @Autowired
  private BlasConfigService blasConfigService;

  @Lazy
  @Autowired
  private StripeService stripeService;

  @Lazy
  @Autowired
  private CardService cardService;

  @PostConstruct
  public void init()
      throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, InvalidKeyException {
    final String hashedSecretKey = blasConfigService.getConfigValueFromKey(STRIPE_PRIVATE_KEY);
    final String blasSecretKey = keyService.getBlasPrivateKey();
    Stripe.apiKey = aesDecrypt(blasSecretKey, hashedSecretKey);
  }

  public Charge charge(final ChargeRequest chargeRequest)
      throws StripeException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    Card card = cardService.getCardInfoByCardId(chargeRequest.getCardId());
    final Map<String, Object> chargeParams = Map.ofEntries(
        Map.entry("amount", chargeRequest.getAmount()),
        Map.entry("currency", chargeRequest.getCurrency()),
        Map.entry("description", chargeRequest.getDescription()),
        Map.entry("source", stripeService.getStripeTransactionToken(card).getId()));
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
    Map<String, Object> params = Map.of("card", cardMap);
    return Token.create(params);
  }
}
