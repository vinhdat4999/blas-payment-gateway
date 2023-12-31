mvn clean verify sonar:sonar \
  -Dsonar.projectKey=blas-payment-gateway \
  -Dsonar.projectName='blas-payment-gateway' \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=sqp_301219d42bef68c00066eff0f5197993f657dd4a
