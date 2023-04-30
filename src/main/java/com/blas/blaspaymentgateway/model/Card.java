package com.blas.blaspaymentgateway.model;

import com.blas.blascommon.core.model.AuthUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cards")
public class Card {

  @Id
  @Column(name = "card_id", length = 50, nullable = false)
  @NotEmpty
  private String cardId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_card_1"))
  @NotNull
  private AuthUser authUser;

  @Column(name = "card_number", length = 200, nullable = false)
  @NotEmpty
  private String cardNumber;

  @Column(name = "card_holder", length = 200, nullable = false)
  @NotEmpty
  private String cardHolder;

  @Column(name = "exp_month", length = 200, nullable = false)
  @NotEmpty
  private String expMonth;

  @Column(name = "exp_year", length = 200, nullable = false)
  @NotEmpty
  private String expYear;

  @Column(name = "cvc", length = 200, nullable = false)
  @NotEmpty
  private String cvc;

  @Column(name = "added_time", nullable = false)
  private LocalDateTime addedTime;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;
}
