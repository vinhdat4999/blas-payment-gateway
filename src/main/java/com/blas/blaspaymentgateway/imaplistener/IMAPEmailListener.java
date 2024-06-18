package com.blas.blaspaymentgateway.imaplistener;

import static com.blas.blascommon.constants.BlasConstant.BLAS_PAYMENT_GATEWAY_IMAP_PASSWORD;
import static com.blas.blascommon.constants.BlasConstant.BLAS_PAYMENT_GATEWAY_IMAP_USERNAME;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.IDLE_CONNECTION_KEEP_ALIVE;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.IMAPS;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.INBOX;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.MESSAGING_EXCEPTION_DURING_IDLE;
import static com.blas.blaspaymentgateway.enums.BankingPaymentErrorCode.IDLE_ERROR;

import com.blas.blascommon.core.service.BlasConfigService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.security.KeyService;
import com.blas.blaspaymentgateway.exception.BankingRequestException;
import com.blas.blaspaymentgateway.service.TimoBankingPaymentService;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IMAPEmailListener extends MessageCountAdapter {

  @Lazy
  private final Session session;

  @Lazy
  private final KeyService keyService;

  @Lazy
  private final BlasConfigService blasConfigService;

  @Lazy
  private final TimoBankingPaymentService timoBankingPaymentService;

  @Lazy
  private final CentralizedLogService centralizedLogService;

  public void startListening()
      throws MessagingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    Store store = session.getStore(IMAPS);

    final String username = aesDecrypt(keyService.getBlasPrivateKey(),
        blasConfigService.getConfigValueFromKey(BLAS_PAYMENT_GATEWAY_IMAP_USERNAME));
    final String password = aesDecrypt(keyService.getBlasPrivateKey(),
        blasConfigService.getConfigValueFromKey(BLAS_PAYMENT_GATEWAY_IMAP_PASSWORD));
    store.connect(username, password);

    IMAPFolder inbox = (IMAPFolder) store.getFolder(INBOX);
    inbox.open(Folder.READ_WRITE);

    Thread keepAliveThread = new Thread(new KeepAliveRunnable(inbox), IDLE_CONNECTION_KEEP_ALIVE);
    keepAliveThread.start();
    log.info("Blas IMAP Banking Payment is listening...");

    inbox.addMessageCountListener(new MessageCountAdapter() {
      @Override
      public void messagesAdded(MessageCountEvent event) {
        Message[] messages = event.getMessages();
        for (Message message : messages) {
          timoBankingPaymentService.handleBankingPayment(message);
        }
      }
    });

    // Start the IDLE Loop
    while (!Thread.interrupted()) {
      try {
        inbox.idle();
      } catch (MessagingException exception) {
        log.error(MESSAGING_EXCEPTION_DURING_IDLE);
        centralizedLogService.saveLog(exception);
        throw new BankingRequestException(IDLE_ERROR, exception);
      }
    }

    // Interrupt and shutdown the keep-alive thread
    if (keepAliveThread.isAlive()) {
      keepAliveThread.interrupt();
    }
  }
}
