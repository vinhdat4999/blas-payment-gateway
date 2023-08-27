package com.blas.blaspaymentgateway.dao;

import com.blas.blaspaymentgateway.model.Card;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CardDao extends JpaRepository<Card, String> {

  @Query("SELECT c FROM Card c WHERE c.cardNumber = :cardNumber")
  Card getCardByCardNumber(@Param("cardNumber") String cardNumber);

  @Query("SELECT c FROM Card c WHERE c.authUser.userId = :userId")
  List<Card> getCardListOfUser(@Param("userId") String userId);
}
