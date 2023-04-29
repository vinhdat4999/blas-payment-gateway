package com.blas.blaspaymentgateway.service;

import com.blas.blaspaymentgateway.model.Card;
import java.util.List;

public interface CardService {

  String addNewCard(Card card);

  List<Card> getCardListOfUser(String userId);

  List<Card> getAllCards();

  Card getCardInfoByCardId(String cardId);

  Card getCardInfoByCardNumber(String cardNumber);
}
