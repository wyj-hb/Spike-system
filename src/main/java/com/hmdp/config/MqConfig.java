package com.hmdp.config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
@Slf4j
@Configuration
public class MqConfig implements ApplicationContextAware {
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        RabbitTemplate mq = applicationContext.getBean(RabbitTemplate.class);
       mq.setReturnCallback(new RabbitTemplate.ReturnCallback() {
           @Override
           public void returnedMessage(Message message, int i, String s, String s1, String s2) {
               log.info("返回消息回调:{} 应答代码:{} 回复文本:{} 交换器:{} 路由键:{}", message, i, s, s1, s2);
           }
       });
    }
}
