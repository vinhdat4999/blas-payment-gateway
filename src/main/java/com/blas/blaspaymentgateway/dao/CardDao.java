package com.blas.blaspaymentgateway.dao;

import com.blas.blaspaymentgateway.model.Card;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CardDao extends JpaRepository<Card, String> {

  @Query("SELECT c FROM Card c WHERE c.cardNumber = ?1")
  Card getCardByCardNumber(String cardNumber);

  @Query("SELECT c FROM Card c WHERE c.authUser.userId = ?1")
  List<Card> getCardListOfUser(String userId);
}
