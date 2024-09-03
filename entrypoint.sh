#!/bin/bash

echo "[server-startup] Starting java application"

echo java -javaagent:./opentelemetry-javaagent.jar \
     -Dotel.exporter.otlp.endpoint=http://otel-collector.default.svc.cluster.local:4317 \
     -Dotel.metrics.exporter=otlp \
     -jar app.jar

java -javaagent:./opentelemetry-javaagent.jar \
     -Dotel.exporter.otlp.endpoint=http://otel-collector.default.svc.cluster.local:4317 \
     -Dotel.metrics.exporter=otlp \
     -jar app.jar
