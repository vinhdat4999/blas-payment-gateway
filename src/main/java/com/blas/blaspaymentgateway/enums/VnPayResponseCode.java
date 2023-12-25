package com.blas.blaspaymentgateway.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VnPayResponseCode {
  RESPONSE_00("00", "Giao dịch thành công"),
  RESPONSE_07("07",
      "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên quan tới lừa đảo, giao dịch bất thường)."),
  RESPONSE_09("09",
      "Giao dịch không thành công do: Thẻ/Tài khoản của khách hàng chưa đăng ký dịch vụ InternetBanking tại ngân hàng."),
  RESPONSE_10("10",
      "Giao dịch không thành công do: Khách hàng xác thực thông tin thẻ/tài khoản không đúng quá 3 lần"),
  RESPONSE_11("11",
      "Giao dịch không thành công do: Đã hết hạn chờ thanh toán. Xin quý khách vui lòng thực hiện lại giao dịch."),
  RESPONSE_12("12", "Giao dịch không thành công do: Thẻ/Tài khoản của khách hàng bị khóa."),
  RESPONSE_13("13",
      "Giao dịch không thành công do Quý khách nhập sai mật khẩu xác thực giao dịch (OTP). Xin quý khách vui lòng thực hiện lại giao dịch."),
  RESPONSE_24("24", "Giao dịch không thành công do: Khách hàng hủy giao dịch"),
  RESPONSE_51("51",
      "Giao dịch không thành công do: Tài khoản của quý khách không đủ số dư để thực hiện giao dịch."),
  RESPONSE_65("65",
      "Giao dịch không thành công do: Tài khoản của Quý khách đã vượt quá hạn mức giao dịch trong ngày."),
  RESPONSE_75("75", "Ngân hàng thanh toán đang bảo trì."),
  RESPONSE_79("79",
      "Giao dịch không thành công do: KH nhập sai mật khẩu thanh toán quá số lần quy định. Xin quý khách vui lòng thực hiện lại giao dịch"),
  RESPONSE_99("99", "Các lỗi khác (lỗi còn lại, không có trong danh sách mã lỗi đã liệt kê)");

  private final String code;
  private final String message;
}
