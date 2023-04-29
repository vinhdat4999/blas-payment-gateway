package com.blas.blaspaymentgateway.exception;

import static java.lang.System.currentTimeMillis;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.blas.blascommon.exceptions.ExceptionHandle;
import com.blas.blascommon.exceptions.ExceptionResponse;
import com.blas.blaspaymentgateway.exception.types.PaymentException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class PaymentExceptionHandle extends ExceptionHandle {

  @ExceptionHandler
  public ResponseEntity<ExceptionResponse> handleException(PaymentException exception) {
    PaymentExceptionResponse error = new PaymentExceptionResponse();
    error.setTransactionId(exception.getTransactionId());
    error.setStripeTransactionId(exception.getStripeTransactionId());
    error.setStatus(BAD_GATEWAY.value());
    error.setMessage(exception.getMessage());
    error.setTimeStamp(currentTimeMillis());
    return new ResponseEntity<>(error, BAD_GATEWAY);
  }
}
