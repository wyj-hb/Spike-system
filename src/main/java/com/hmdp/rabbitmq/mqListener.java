package com.hmdp.rabbitmq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Component
@Slf4j
public class mqListener
{
    @Resource
    IVoucherOrderService voucherOrderService;
    @Resource
    ISeckillVoucherService seckillVoucherService;
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "PlaceOrder"),
            exchange = @Exchange(name = "spike.first", type = ExchangeTypes.TOPIC),
            key = "place.order"))
    @Transactional(rollbackFor = Exception.class)
    public void order_consumer(VoucherOrder voucherOrder) {
        log.info("消费者线程" + Thread.currentThread() + "接收到了消息:"+ voucherOrder);
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)//cas乐观锁
                .update();
        if(!success){
            log.error("库存不足");
            return;
        }
        //直接保存订单
        voucherOrderService.save(voucherOrder);
    }
}
