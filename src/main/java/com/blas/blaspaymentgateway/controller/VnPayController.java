package com.blas.blaspaymentgateway.controller;

import static com.blas.blascommon.enums.EmailTemplate.VNPAY_PAYMENT_RECEIPT;
import static com.blas.blascommon.security.SecurityUtils.aesDecrypt;
import static com.blas.blaspaymentgateway.constants.PaymentGateway.SUBJECT_EMAIL_RECEIPT;
import static com.blas.blaspaymentgateway.constants.VnPay.HASHED_TRANSACTION_DATE;
import static com.blas.blaspaymentgateway.constants.VnPay.HASHED_VNP_TXN_REF;
import static com.blas.blaspaymentgateway.constants.VnPay.PAYMENT_TRANSACTION_ID;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_MESSAGE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_PAY_DATE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_PROMOTION_AMOUNT;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_PROMOTION_CODE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_RESPONSE_CODE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_RESPONSE_ID;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_TMN_CODE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_TRANSACTION_STATUS;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_TRANSACTION_TYPE;
import static com.blas.blaspaymentgateway.constants.VnPay.VN_PAY;
import static com.blas.blaspaymentgateway.enums.VnPayResponseCode.RESPONSE_00;
import static java.lang.Integer.parseInt;
import static java.net.URLDecoder.decode;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

import com.blas.blascommon.configurations.EmailQueueService;
import com.blas.blascommon.core.model.AuthUser;
import com.blas.blascommon.core.model.UserDetail;
import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.exceptions.types.NotFoundException;
import com.blas.blascommon.payload.HtmlEmailRequest;
import com.blas.blascommon.payload.payment.VnPayChargeRequest;
import com.blas.blascommon.security.KeyService;
import com.blas.blaspaymentgateway.model.VnPayPaymentTransactionLog;
import com.blas.blaspaymentgateway.service.VnPayPaymentTransactionLogService;
import com.blas.blaspaymentgateway.service.merchants.VnPayService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class VnPayController {

  @Lazy
  private final VnPayService vnPayService;

  @Lazy
  private final KeyService keyService;

  @Lazy
  private final AuthUserService authUserService;

  @Lazy
  private final VnPayPaymentTransactionLogService vnPayPaymentTransactionLogService;

  @Lazy
  private final EmailQueueService emailQueueService;

  @PostMapping(value = "/vnpay/charge")
  public String charge(@RequestBody VnPayChargeRequest chargeRequest, HttpServletRequest request)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    return vnPayService.createPayUrlVnPay(request,
        chargeRequest.getAmount(),
        15,
        chargeRequest.getCurrency(),
        chargeRequest.getBankCode(),
        chargeRequest.getDescription());
  }

  @GetMapping(value = "/vnpay/handle-payment")
  public String handlePayment(
      @RequestParam(value = PAYMENT_TRANSACTION_ID) String paymentTransactionLogId,
      @RequestParam(value = HASHED_VNP_TXN_REF) String hashedVnpTxnRef,
      @RequestParam(value = HASHED_TRANSACTION_DATE) String hashedTransactionDate,
      HttpServletRequest request)
      throws IOException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    final String blasPrivateKey = keyService.getBlasPrivateKey();
    final String vnpTxnRef = decode(aesDecrypt(blasPrivateKey, hashedVnpTxnRef), UTF_8);
    final String transactionDate = decode(aesDecrypt(blasPrivateKey, hashedTransactionDate), UTF_8);

    JSONObject response = vnPayService.checkOrder(request, vnpTxnRef, transactionDate);
    final String vnpResponseCode = response.optString(VNP_RESPONSE_CODE);
    if (!RESPONSE_00.getCode().equals(vnpResponseCode)) {
      throw new BadRequestException(response.optString(VNP_MESSAGE));
    }

    VnPayPaymentTransactionLog vnPayPaymentTransactionLog = vnPayPaymentTransactionLogService.getVnPayPaymentTransactionLog(
        paymentTransactionLogId);
    if (vnPayPaymentTransactionLog == null) {
      throw new NotFoundException(
          String.format("VNPAY transaction log ID %s not found", paymentTransactionLogId));
    }

    vnPayPaymentTransactionLog.setVnpPayDate(response.getString(VNP_PAY_DATE));
    vnPayPaymentTransactionLog.setVnpResponseId(response.getString(VNP_RESPONSE_ID));
    vnPayPaymentTransactionLog.setVnpTmnCode(response.getString(VNP_TMN_CODE));
    vnPayPaymentTransactionLog.setVnpResponseCode(vnpResponseCode);
    vnPayPaymentTransactionLog.setVnpMessage(response.getString(VNP_MESSAGE));
    vnPayPaymentTransactionLog.setVnpTransactionType(response.getString(VNP_TRANSACTION_TYPE));
    vnPayPaymentTransactionLog.setVnpTransactionStatus(response.getString(VNP_TRANSACTION_STATUS));
    vnPayPaymentTransactionLog.setVnpPromotionCode(response.optString(VNP_PROMOTION_CODE));
    vnPayPaymentTransactionLog.setVnpPromotionAmount(
        parseInt(defaultIfBlank(response.optString(VNP_PROMOTION_AMOUNT), "0")));

    vnPayPaymentTransactionLogService.saveVnPayPaymentTransactionLog(vnPayPaymentTransactionLog);

    new Thread(
        () -> sendVnPayReceiptEmail(vnPayPaymentTransactionLog.getAuthUser().getUsername(),
            vnPayPaymentTransactionLog)).start();
    return response.toString();
  }

  private void sendVnPayReceiptEmail(String username,
      VnPayPaymentTransactionLog vnPayPaymentTransactionLog) {
    AuthUser authUser = authUserService.getAuthUserByUsername(username);
    UserDetail userDetail = authUser.getUserDetail();

    HtmlEmailRequest htmlEmailRequest = new HtmlEmailRequest();
    htmlEmailRequest.setEmailTo(userDetail.getEmail());
    htmlEmailRequest.setTitle(SUBJECT_EMAIL_RECEIPT);
    htmlEmailRequest.setEmailTemplateName(VNPAY_PAYMENT_RECEIPT.name());
    htmlEmailRequest.setData(Map.ofEntries(
        Map.entry("email", userDetail.getEmail()),
        Map.entry("phone", userDetail.getPhoneNumber()),
        Map.entry("name", userDetail.getFirstName() + SPACE + userDetail
            .getLastName()),
        Map.entry("transactionId", vnPayPaymentTransactionLog.getPaymentTransactionLogId()),
        Map.entry("transactionTime", vnPayPaymentTransactionLog.getTransactionTime().toString()),
        Map.entry("bank", defaultIfBlank(vnPayPaymentTransactionLog.getVnpBankCode(), VN_PAY)),
        Map.entry("status", RESPONSE_00.getMessage()),
        Map.entry("description", vnPayPaymentTransactionLog.getOrderInfo()),
        Map.entry("amount", String.valueOf(vnPayPaymentTransactionLog.getAmount() / 100)),
        Map.entry("currency", vnPayPaymentTransactionLog.getCurrency())
    ));

    emailQueueService.sendMessage(new JSONArray(List.of(htmlEmailRequest)).toString());
  }
}
