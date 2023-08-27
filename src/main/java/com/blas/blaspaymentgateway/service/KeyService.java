package com.blas.blaspaymentgateway.service;

import static com.blas.blascommon.exceptions.BlasErrorCode.MSG_BLAS_APP_FAILURE;
import static com.blas.blascommon.security.SecurityUtils.getPrivateKeyAesFromCertificate;

import com.blas.blascommon.exceptions.types.BadRequestException;
import com.blas.blascommon.properties.BlasPrivateKeyConfiguration;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class KeyService {

  @Autowired
  private String getCertPassword;

  @Lazy
  @Autowired
  private BlasPrivateKeyConfiguration blasPrivateKeyConfiguration;

  public String getBlasPrivateKey() {
    try {
      return getPrivateKeyAesFromCertificate(blasPrivateKeyConfiguration.getCertificate(),
          blasPrivateKeyConfiguration.getAliasBlasPrivateKey(), getCertPassword);
    } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException |
             UnrecoverableKeyException exception) {
      throw new BadRequestException(MSG_BLAS_APP_FAILURE);
    }
  }
}
