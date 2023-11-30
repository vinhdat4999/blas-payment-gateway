package com.blas.blaspaymentgateway.configuration;

import static com.blas.blascommon.constants.MessageTopic.BLAS_EMAIL_QUEUE;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EmailQueueService {

  private final IQueue<String> topic;

  @Autowired
  public EmailQueueService(HazelcastInstance hazelcastInstance) {
    this.topic = hazelcastInstance.getQueue(BLAS_EMAIL_QUEUE);
  }

  public void sendMessage(String message) {
    topic.offer(message);
  }
}
