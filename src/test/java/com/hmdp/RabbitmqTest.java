package com.hmdp;

import com.hmdp.entity.VoucherOrder;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
@RunWith(SpringRunner.class)
@SpringBootTest
public class RabbitmqTest {
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Test
    public void testSendMessage() throws IOException, TimeoutException {
        // 1.建立连接
        ConnectionFactory factory = new ConnectionFactory();
        // 1.1.设置连接参数，分别是：主机名、端口号、vhost、用户名、密码
        factory.setHost("192.168.43.11");
        factory.setPort(5673);
        factory.setVirtualHost("/");
        factory.setUsername("wyj");
        factory.setPassword("123321");
        // 1.2.建立连接
        Connection connection = factory.newConnection();
        // 2.创建通道Channel
        Channel channel = connection.createChannel();
        // 3.创建队列
        String queueName = "simple.queue";
        channel.queueDeclare(queueName, false, false, false, null);
        // 4.发送消息
        String message = "hello, rabbitmq!";
        channel.basicPublish("", queueName, null, message.getBytes());
        System.out.println("发送消息成功：【" + message + "】");
        // 5.关闭通道和连接
        channel.close();
        connection.close();
    }
    @Test
    public void testSendMessage2SimpleQueue() {
        String exchangeName = "spike.first";
        VoucherOrder data = new VoucherOrder();
        data.setVoucherId(10L);
        data.setUserId(100L);
        rabbitTemplate.convertAndSend(exchangeName, "test",data);
    }
}
