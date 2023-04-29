package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.utils.StringUtils.SPACE;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;

import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blaspaymentgateway.payload.ChargeRequest;
import com.blas.blaspaymentgateway.payload.ChargeResponse;
import com.blas.blaspaymentgateway.service.CardService;
import com.blas.blaspaymentgateway.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class ChargeController {

  @Lazy
  @Autowired
  private StripeService paymentsService;

  @Lazy
  @Autowired
  private CardService cardService;

  @PostMapping(value = "/charge")
  public ResponseEntity<?> charge(@RequestBody ChargeRequest chargeRequest) {
    if (!getUsernameLoggedIn().equals(
        cardService.getCardInfoByCardId(chargeRequest.getCardId()).getAuthUser().getUsername())) {
      throw new BadRequestException(INVALID_CARD);
    }
    Charge charge;
    try {
      charge = paymentsService.charge(chargeRequest);
    } catch (StripeException exception) {
      return ResponseEntity.ok(exception.getMessage());
    } catch (IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException |
             InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException exception) {
      throw new BadRequestException(exception.getMessage());
    }
    return ResponseEntity.ok(
        buildChargeResponse(charge, chargeRequest.getCardId(), getUsernameLoggedIn()));
  }

  private ChargeResponse buildChargeResponse(Charge charge, String cardId, String username) {
    return ChargeResponse.builder()
        .transactionId(charge.getId())
        .amountCaptured(
            (double) (charge.getAmountCaptured()) / 100 + SPACE + charge.getCurrency()
                .toUpperCase())
        .username(username)
        .cardId(cardId)
        .receiptUrl(charge.getReceiptUrl())
        .status(charge.getStatus().toUpperCase())
        .description(charge.getDescription())
        .transactionTime(
            LocalDateTime.ofEpochSecond(charge.getCreated(), 0, ZoneOffset.UTC).minusHours(-7))
        .build();
  }
}
