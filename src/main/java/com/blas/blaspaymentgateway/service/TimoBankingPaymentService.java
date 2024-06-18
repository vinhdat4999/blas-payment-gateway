package com.blas.blaspaymentgateway.service;

import static com.blas.blascommon.constants.BlasConstant.GOOGLE_SHEET_CLIENT_KEY;
import static com.blas.blascommon.constants.BlasConstant.GOOGLE_SHEET_CLIENT_VALUE;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.StringUtils.DOT;
import static com.blas.blascommon.utils.StringUtils.NEW_LINE_CHARACTER;
import static com.blas.blascommon.utils.httprequest.HttpMethod.POST;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.BANKING_PAYMENT_ID_PREFIX;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.CAN_NOT_GET_PAYMENT_CODE;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.LENGTH_OF_BANKING_PAYMENT_CODE;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.POST_STATUS_NOT_YET;
import static com.blas.blaspaymentgateway.constants.IMAPBankingPayment.RECEIVED_STATUS;
import static com.blas.blaspaymentgateway.enums.BankingPaymentErrorCode.EXTRACTING_EMAIL_ERROR;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.substring;

import com.blas.blascommon.configurations.ObjectMapperConfiguration;
import com.blas.blascommon.core.service.BlasConfigService;
import com.blas.blascommon.core.service.CentralizedLogService;
import com.blas.blascommon.exceptions.types.UnauthorizedException;
import com.blas.blascommon.payload.HttpResponse;
import com.blas.blascommon.security.KeyService;
import com.blas.blascommon.utils.httprequest.HttpRequest;
import com.blas.blaspaymentgateway.exception.BankingRequestException;
import com.blas.blaspaymentgateway.model.BankingPaymentRequest;
import com.blas.blaspaymentgateway.payload.BankingPaymentResponse;
import com.blas.blaspaymentgateway.payload.BankingResultHostRequest;
import com.blas.blaspaymentgateway.properties.BankingResultHostProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMultipart;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimoBankingPaymentService {

  private static final String TIMO_TRANSACTION_EMAIL_SUBJECT = "Thông báo thay đổi số dư tài khoản";
  private static final String SUPPORT_TIMO_VN_EMAIL = "support@timo.vn";
  private static final String AMOUNT_START_PREFIX = "Tài khoản Spend Account vừa tăng ";
  private static final String AMOUNT_END_POSTFIX = "VND vào";
  private static final String DESCRIPTION_START_PREFIX = "<p>Mô tả: ";
  private static final String DESCRIPTION_END_INDEX = "</p>";
  private static final String ERROR_401_UNAUTHORIZED = "Error 401. Unauthorized";
  private static final String TRY_TO_POST_BUT_UNAUTHORIZED = "TRY TO POST BUT UNAUTHORIZED";

  @Lazy
  private final BankingPaymentRequestService bankingPaymentRequestService;

  @Lazy
  private final BankingResultHostProperties bankingResultHostProperties;

  @Lazy
  private final HttpRequest httpRequest;

  @Lazy
  private final BlasConfigService blasConfigService;

  @Lazy
  private final CentralizedLogService centralizedLogService;

  @Lazy
  private final KeyService keyService;

  @Lazy
  private final ObjectMapperConfiguration objectMapperConfiguration;

  public void handleBankingPayment(Message message) {
    BankingPaymentRequest bankingPaymentRequest;
    try {
      bankingPaymentRequest = extractEmailContent(message);
    } catch (MessagingException | IOException exception) {
      throw new BankingRequestException(EXTRACTING_EMAIL_ERROR, exception);
    }

    if (bankingPaymentRequest != null) {
      log.info("Start to handle banking payment request...");
      log.info("Banking payment request:{}", bankingPaymentRequest);

      bankingPaymentRequestService.save(bankingPaymentRequest);
      try {
        postBankingPayment(bankingPaymentRequest);
      } catch (IOException | InvocationTargetException | NoSuchMethodException |
               InstantiationException | IllegalAccessException |
               InvalidAlgorithmParameterException | IllegalBlockSizeException |
               BadPaddingException | NoSuchPaddingException | NoSuchAlgorithmException |
               InvalidKeyException exception) {
        log.error(
            "Error when trying to handle Banking Payment Request. Banking Payment Request ID: {}",
            bankingPaymentRequest.getPaymentId());
        centralizedLogService.saveLog(exception, bankingPaymentRequest, message, null);
      }
      log.info("Banking payment request has been handled completely.");
    }
  }

  private void postBankingPayment(BankingPaymentRequest bankingPaymentRequest)
      throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    BankingResultHostRequest bankingResultHostRequest = BankingResultHostRequest.builder()
        .postJobType(bankingResultHostProperties.getPostJobType())
        .username(aesDecrypt(keyService.getBlasPrivateKey(),
            blasConfigService.getConfigValueFromKey(GOOGLE_SHEET_CLIENT_KEY)))
        .password(aesDecrypt(keyService.getBlasPrivateKey(),
            blasConfigService.getConfigValueFromKey(GOOGLE_SHEET_CLIENT_VALUE)))
        .bankingPaymentRequest(bankingPaymentRequest)
        .build();

    HttpResponse response = httpRequest.sendRequestWithJsonObjectPayload(
        bankingResultHostProperties.getEndpointUrl(), POST,
        null, null, new JSONObject(bankingResultHostRequest));
    postHandlerResponse(response, bankingPaymentRequest);

  }

  private void postHandlerResponse(HttpResponse response,
      BankingPaymentRequest bankingPaymentRequest) throws JsonProcessingException {
    String responseStr = response.getResponse();
    if (contains(responseStr, ERROR_401_UNAUTHORIZED)) {
      bankingPaymentRequest.setPostStatus(TRY_TO_POST_BUT_UNAUTHORIZED);
      bankingPaymentRequestService.save(bankingPaymentRequest);
      log.error(
          "Tried to post Banking Payment Request but unauthorized. Banking Payment Request ID: {}",
          bankingPaymentRequest.getPaymentId());
      centralizedLogService.saveLog(new UnauthorizedException(TRY_TO_POST_BUT_UNAUTHORIZED),
          response, bankingPaymentRequest, null);
      return;
    }

    BankingPaymentResponse bankingPaymentResponse = objectMapperConfiguration.objectMapper()
        .readValue(responseStr, BankingPaymentResponse.class);

    String message = bankingPaymentResponse.getMessage();
    bankingPaymentRequest.setPostStatus(message);
    bankingPaymentRequest.setHostResponse(responseStr);
    log.info("Complete transaction. Message from Debt server: {}. Banking Payment Request ID: {}",
        message, bankingPaymentRequest.getPaymentId());
    bankingPaymentRequestService.save(bankingPaymentRequest);
  }

  private BankingPaymentRequest extractEmailContent(Message message)
      throws MessagingException, IOException {
    if (!isPaymentEmail(message.getSubject(), message.getFrom()[0].toString())) {
      return null;
    }

    String body =
        message.getContent() instanceof MimeMultipart mimeMultipart ? getMimeMultipartContent(
            mimeMultipart) : message.getContent().toString();
    int amountStartIndex = body.indexOf(AMOUNT_START_PREFIX) + AMOUNT_START_PREFIX.length();
    int amountEndIndex = body.indexOf(AMOUNT_END_POSTFIX, amountStartIndex);
    int amount = Integer.parseInt(
        substring(body, amountStartIndex, amountEndIndex).trim().replace(DOT, EMPTY));

    int descriptionStartIndex =
        body.indexOf(DESCRIPTION_START_PREFIX) + DESCRIPTION_START_PREFIX.length();
    int descriptionEndIndex = body.indexOf(DESCRIPTION_END_INDEX, descriptionStartIndex);
    String fullDescription = substring(body, descriptionStartIndex, descriptionEndIndex);

    int paymentCodeStartIndex = fullDescription.toUpperCase().indexOf(BANKING_PAYMENT_ID_PREFIX);
    String paymentCode = EMPTY;
    String note;
    if (paymentCodeStartIndex == -1) {
      note = CAN_NOT_GET_PAYMENT_CODE;
    } else {
      note = RECEIVED_STATUS;
      paymentCode = substring(fullDescription, paymentCodeStartIndex,
          paymentCodeStartIndex + LENGTH_OF_BANKING_PAYMENT_CODE);
    }

    return BankingPaymentRequest.builder()
        .paymentId(genUUID())
        .blasProcessTime(LocalDateTime.now())
        .receivedTime(message.getReceivedDate())
        .amount(amount)
        .paymentCode(paymentCode.toUpperCase())
        .fullDescription(fullDescription)
        .note(note)
        .postStatus(POST_STATUS_NOT_YET)
        .build();
  }

  private boolean isPaymentEmail(String subject, String from) {
    return TIMO_TRANSACTION_EMAIL_SUBJECT.equals(subject) && contains(from, SUPPORT_TIMO_VN_EMAIL);
  }

  private String getMimeMultipartContent(MimeMultipart mimeMultipart)
      throws MessagingException, IOException {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < mimeMultipart.getCount(); index++) {
      BodyPart bodyPart = mimeMultipart.getBodyPart(index);
      if (bodyPart.isMimeType("text/plain")) {
        builder.append(bodyPart.getContent()).append(NEW_LINE_CHARACTER);
      } else if (bodyPart.isMimeType("text/html")) {
        builder.append(bodyPart.getContent()).append(NEW_LINE_CHARACTER);
      } else if (bodyPart.getContent() instanceof MimeMultipart mimeMultipartInner) {
        builder.append(getMimeMultipartContent(mimeMultipartInner));
      } else {
        builder.append(bodyPart.getContent()).append(NEW_LINE_CHARACTER);
      }
    }
    return builder.toString();
  }
}
