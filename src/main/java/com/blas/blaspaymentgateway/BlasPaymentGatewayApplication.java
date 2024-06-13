package com.blas.blaspaymentgateway;

import com.blas.blaspaymentgateway.imaplistener.IMAPEmailListener;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EntityScan("com.blas")
@ComponentScan(basePackages = {"com.blas"})
@EnableJpaRepositories(basePackages = {"com.blas.blascommon.core.dao.jpa",
    "com.blas.blaspaymentgateway.dao"})
@EnableMongoRepositories(basePackages = "com.blas.blascommon.core.dao.mongodb")
@SpringBootApplication
public class BlasPaymentGatewayApplication {

  private final IMAPEmailListener imapEmailListener;

  public BlasPaymentGatewayApplication(IMAPEmailListener imapEmailListener) {
    this.imapEmailListener = imapEmailListener;
  }

  public static void main(String[] args) {
    SpringApplication.run(BlasPaymentGatewayApplication.class, args);
  }

  @PostConstruct
  public void startListener() {
    new Thread(() -> {
      try {
        imapEmailListener.startListening();
      } catch (MessagingException | InvalidAlgorithmParameterException | IllegalBlockSizeException |
               NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException |
               InvalidKeyException e) {
        e.printStackTrace();
      }
    }).start();
  }
}
