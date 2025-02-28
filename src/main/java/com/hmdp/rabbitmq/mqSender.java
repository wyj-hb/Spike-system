package com.hmdp.rabbitmq;

import com.hmdp.entity.VoucherOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
@Slf4j
@Component
public class mqSender {
    @Resource
    private RabbitTemplate rabbitTemplate;
    public void send(String exchangeName, String routingKey, VoucherOrder voucherOrder) {
        log.info("生产者线程" + Thread.currentThread() + "发送消息:"+ voucherOrder);
        rabbitTemplate.convertAndSend(exchangeName, routingKey, voucherOrder);
    }
}
