package com.blas.blaspaymentgateway.imaplistener;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.imap.IMAPFolder;

@Slf4j
public class KeepAliveRunnable implements Runnable {

  private static final long KEEP_ALIVE_FREQ = 300000; // 5 minutes

  private final IMAPFolder folder;

  public KeepAliveRunnable(IMAPFolder folder) {
    this.folder = folder;
  }

  @Override
  public void run() {
    while (!Thread.interrupted()) {
      try {
        Thread.sleep(KEEP_ALIVE_FREQ);

        // Perform a NOOP to keep the connection alive
        log.info("Performing a NOOP to keep the connection alive");
        folder.doCommand(protocol -> {
          protocol.simpleCommand("NOOP", null);
          return null;
        });
      } catch (MessagingException exception) {
        log.error("Unexpected exception while keeping alive the IDLE connection", exception);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        log.error("Thread was interrupted", exception);
      }
    }
  }
}
