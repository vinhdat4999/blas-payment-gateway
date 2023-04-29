mvn clean verify sonar:sonar \
  -Dsonar.projectKey=blas-payment-gateway \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=sqp_92e24b8c700009222a6b24a615dadff7349d5fae
