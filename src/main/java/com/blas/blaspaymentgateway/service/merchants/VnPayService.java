package com.blas.blaspaymentgateway.service.merchants;

import static com.blas.blascommon.constants.MDCConstant.GLOBAL_ID;
import static com.blas.blascommon.security.SecurityUtils.aesEncrypt;
import static com.blas.blascommon.security.SecurityUtils.getUsernameLoggedIn;
import static com.blas.blascommon.security.SecurityUtils.hmacSHA512;
import static com.blas.blascommon.utils.IdUtils.genNumericID;
import static com.blas.blascommon.utils.IdUtils.genUUID;
import static com.blas.blascommon.utils.IpUtils.getIpAddress;
import static com.blas.blascommon.utils.datetimeutils.DateTimeUtils.DATE_YYYYMMDDHHMMSS_SLASH_FORMAT;
import static com.blas.blascommon.utils.datetimeutils.DateTimeUtils.GMT7_POSITIVE_ZONE;
import static com.blas.blaspaymentgateway.constants.VnPay.HASHED_TRANSACTION_DATE;
import static com.blas.blaspaymentgateway.constants.VnPay.HASHED_VNP_TXN_REF;
import static com.blas.blaspaymentgateway.constants.VnPay.LANGUAGE;
import static com.blas.blaspaymentgateway.constants.VnPay.PAYMENT_TRANSACTION_ID;
import static com.blas.blaspaymentgateway.constants.VnPay.VN;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_AMOUNT;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_BANK_CODE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_COMMAND;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_CREATE_DATE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_CURR_CODE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_EXPIRE_DATE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_IP_ADDRESS;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_LOCALE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_ORDER_INFO;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_ORDER_TYPE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_REQUEST_ID;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_RETURN_URL;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_SECURE_HASH;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_TMN_CODE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_TRANSACTION_DATE;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_TXN_REF;
import static com.blas.blaspaymentgateway.constants.VnPay.VNP_VERSION;
import static com.blas.blaspaymentgateway.enums.VnPayCommand.PAY;
import static com.blas.blaspaymentgateway.enums.VnPayCommand.QUERYDR;
import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.blas.blascommon.core.service.AuthUserService;
import com.blas.blascommon.core.service.BlasConfigService;
import com.blas.blascommon.enums.Currency;
import com.blas.blascommon.payload.HttpResponse;
import com.blas.blascommon.security.KeyService;
import com.blas.blascommon.utils.httprequest.HttpMethod;
import com.blas.blascommon.utils.httprequest.HttpRequest;
import com.blas.blaspaymentgateway.model.VnPayPaymentTransactionLog;
import com.blas.blaspaymentgateway.properties.VnPayProperties;
import com.blas.blaspaymentgateway.service.VnPayPaymentTransactionLogService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VnPayService {

  @Lazy
  private final VnPayProperties vnPayProperties;

  @Lazy
  private final VnPayPaymentTransactionLogService vnPayPaymentTransactionLogService;

  @Lazy
  private final AuthUserService authUserService;

  @Lazy
  private final KeyService keyService;

  @Lazy
  private final BlasConfigService blasConfigService;

  @Lazy
  private final HttpRequest httpRequest;

  public String createPayUrlVnPay(HttpServletRequest request, long amount, int expiredInMinus,
      Currency currency, String bankCode, String description)
      throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException {
    final String vnpTxnRef = genNumericID(vnPayProperties.getVnpTxnRefLength());

    Map<String, String> vnpParams = new HashMap<>();
    vnpParams.put(VNP_VERSION, vnPayProperties.getVersion());
    vnpParams.put(VNP_COMMAND, PAY.getCommand());
    final String blasPrivateKey = keyService.getBlasPrivateKey();
    vnpParams.put(VNP_TMN_CODE, vnPayProperties.getTmnCode());
    vnpParams.put(VNP_AMOUNT, String.valueOf(amount));
    vnpParams.put(VNP_CURR_CODE, currency.toString());

    Optional.ofNullable(bankCode)
        .filter(StringUtils::isNotBlank)
        .ifPresent(code -> vnpParams.put(VNP_BANK_CODE, code));

    vnpParams.put(VNP_TXN_REF, vnpTxnRef);
    String orderInfo = description + SPACE + vnpTxnRef;
    vnpParams.put(VNP_ORDER_INFO, orderInfo);
    vnpParams.put(VNP_ORDER_TYPE, vnPayProperties.getOrderType());

    vnpParams.put(VNP_LOCALE, defaultIfBlank(request.getParameter(LANGUAGE), VN));
    vnpParams.put(VNP_IP_ADDRESS, getIpAddress(request));

    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(GMT7_POSITIVE_ZONE));
    SimpleDateFormat formatter = new SimpleDateFormat(DATE_YYYYMMDDHHMMSS_SLASH_FORMAT);
    String vnpCreateDate = formatter.format(calendar.getTime());
    vnpParams.put(VNP_CREATE_DATE, vnpCreateDate);
    final String hashedVnpTxnRef = aesEncrypt(blasPrivateKey, vnpTxnRef);
    final String hashedTransactionDate = aesEncrypt(blasPrivateKey, vnpCreateDate);
    final String paymentTransactionLogId = genUUID();
    vnpParams.put(VNP_RETURN_URL,
        String.format(
            "%s/vnpay/handle-payment?%s=%s&%s=%s&%s=%s",
            vnPayProperties.getReturnUrl(), PAYMENT_TRANSACTION_ID,
            encode(paymentTransactionLogId, UTF_8), HASHED_VNP_TXN_REF,
            encode(hashedVnpTxnRef, UTF_8), HASHED_TRANSACTION_DATE,
            encode(hashedTransactionDate, UTF_8)));

    calendar.add(Calendar.MINUTE, expiredInMinus);
    String vnpExpireDate = formatter.format(calendar.getTime());
    vnpParams.put(VNP_EXPIRE_DATE, vnpExpireDate);

    List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
    Collections.sort(fieldNames);
    StringBuilder hashData = new StringBuilder();
    StringBuilder query = new StringBuilder();
    Iterator<String> iterator = fieldNames.iterator();
    while (iterator.hasNext()) {
      String fieldName = iterator.next();
      String fieldValue = vnpParams.get(fieldName);

      if (isNotBlank(fieldValue)) {
        hashData.append(fieldName);
        hashData.append('=');
        hashData.append(encode(fieldValue, US_ASCII));
        query.append(encode(fieldName, US_ASCII));
        query.append('=');
        query.append(encode(fieldValue, US_ASCII));
        if (iterator.hasNext()) {
          query.append('&');
          hashData.append('&');
        }
      }
    }
    String queryUrl = query.toString();
    String vnpSecureHash = hmacSHA512(vnPayProperties.getPrivateKey(), hashData.toString());
    queryUrl += "&" + VNP_SECURE_HASH + "=" + vnpSecureHash;
    vnPayPaymentTransactionLogService.createVnPayPaymentTransactionLog(
        VnPayPaymentTransactionLog.builder()
            .paymentTransactionLogId(paymentTransactionLogId)
            .globalId(MDC.get(GLOBAL_ID))
            .txnRef(vnpTxnRef)
            .transactionTime(LocalDateTime.now())
            .authUser(authUserService.getAuthUserByUsername(getUsernameLoggedIn()))
            .command(PAY.getCommand())
            .amount(amount)
            .currency(currency.toString())
            .vnpBankCode(bankCode)
            .orderInfo(orderInfo)
            .orderType(vnPayProperties.getOrderType())
            .locale(vnpParams.get(VNP_LOCALE))
            .ipAddress(vnpParams.get(VNP_IP_ADDRESS))
            .createDate(vnpCreateDate)
            .expireDate(vnpExpireDate)
            .build());
    log.info("VNPAY URL created. payment_transaction_log_id: {}", paymentTransactionLogId);
    return vnPayProperties.getPayUrl() + "?" + queryUrl;
  }

  public JSONObject checkOrder(HttpServletRequest request, String vnpTxnRef, String transactionDate)
      throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
    String vnpRequestId = genNumericID(vnPayProperties.getVnpTxnRefLength());
    String vnpOrderInfo = "Kiem tra ket qua GD OrderId:" + vnpTxnRef;

    Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(GMT7_POSITIVE_ZONE));
    SimpleDateFormat formatter = new SimpleDateFormat(DATE_YYYYMMDDHHMMSS_SLASH_FORMAT);
    String vnpCreateDate = formatter.format(calendar.getTime());

    JSONObject vnpParams = new JSONObject();
    vnpParams.put(VNP_REQUEST_ID, vnpRequestId);
    vnpParams.put(VNP_VERSION, vnPayProperties.getVersion());
    vnpParams.put(VNP_COMMAND, QUERYDR.getCommand());
    final String vnpTmnCode = vnPayProperties.getTmnCode();
    vnpParams.put(VNP_TMN_CODE, vnpTmnCode);
    vnpParams.put(VNP_TXN_REF, vnpTxnRef);
    vnpParams.put(VNP_ORDER_INFO, vnpOrderInfo);
    vnpParams.put(VNP_TRANSACTION_DATE, transactionDate);
    vnpParams.put(VNP_CREATE_DATE, vnpCreateDate);
    final String ipAddress = getIpAddress(request);
    vnpParams.put(VNP_IP_ADDRESS, ipAddress);

    String hashData = String.join("|", vnpRequestId, vnPayProperties.getVersion(),
        QUERYDR.getCommand(), vnpTmnCode, vnpTxnRef, transactionDate, vnpCreateDate, ipAddress,
        vnpOrderInfo);
    String vnpSecureHash = hmacSHA512(vnPayProperties.getPrivateKey(), hashData);
    vnpParams.put(VNP_SECURE_HASH, vnpSecureHash);

    HttpResponse response = httpRequest.sendRequestWithJsonObjectPayload(
        vnPayProperties.getCheckOrderUrl(),
        HttpMethod.POST, null, null, vnpParams);
    log.info("Check VNPay order result. vnpRequestId: {}, transactionDate: {}", vnpRequestId,
        transactionDate);
    return new JSONObject(response.getResponse());
  }
}
