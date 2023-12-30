package com.blas.blaspaymentgateway.service.impl;

import static com.blas.blascommon.constants.ResponseMessage.USER_ID_NOT_FOUND;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.CARD_ID_NOT_FOUND;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.INVALID_CARD;

import com.blas.blascommon.core.dao.jpa.AuthUserDao;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.NotFoundException;
import com.blas.blaspaymentgateway.dao.CardDao;
import com.blas.blaspaymentgateway.model.Card;
import com.blas.blaspaymentgateway.service.CardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = {Exception.class, Throwable.class})
public class CardServiceImpl implements CardService {

  @Lazy
  private final CardDao cardDao;

  @Lazy
  private final AuthUserDao authUserDao;

  @Override
  public String addNewCard(Card card) {
    card.setCardId(genUUID());
    return cardDao.save(card).getCardId();
  }

  @Override
  public List<Card> getCardListOfUser(String userId) {
    authUserDao.findById(userId).orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND));
    return cardDao.getCardListOfUser(userId);
  }

  @Override
  public List<Card> getAllCards() {
    return cardDao.findAll();
  }

  @Override
  public Card getCardInfoByCardId(String cardId, boolean isAbstractMessage) {
    return cardDao.findById(cardId)
        .orElseThrow(() -> isAbstractMessage ?
            new BadRequestException(INVALID_CARD) :
            new NotFoundException(CARD_ID_NOT_FOUND));
  }

  @Override
  public Card getCardInfoByCardNumber(String cardNumber) {
    return cardDao.getCardByCardNumber(cardNumber);
  }
}
