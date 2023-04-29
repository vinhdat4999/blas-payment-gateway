package com.blas.blaspaymentgateway.service;

import static com.blas.blascommon.security.SecurityUtils.getPrivateKeyAesFromCertificate;

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
             UnrecoverableKeyException e) {
      throw new RuntimeException(e);
    }
  }
}
