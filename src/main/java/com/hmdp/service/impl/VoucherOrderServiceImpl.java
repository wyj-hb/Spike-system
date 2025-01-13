package com.hmdp.service.impl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXCUTOR = Executors.newSingleThreadExecutor();
    private IVoucherOrderService proxy;
    @PostConstruct
    private void init()
    {
        SECKILL_ORDER_EXCUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true)
            {
                //1.获取队列中的订单信息
                try {
                    //获取队列中的订单信息
                    VoucherOrder take = orderTasks.take();
                    //创建订单
                    handleVoucherOrder(take);
                } catch (Exception e) {
                    log.error("处理订单异常");
                }

            }
        }
    }
    private void handleVoucherOrder(VoucherOrder take) {
        Long userId = take.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean islock = lock.tryLock();
        //判断是否成功
        if(!islock)
        {
            //失败则返回错误信息
            log.error("不允许重复下单");
            return;
        }
        try {

            proxy.createVoucherOrder(take);
        } finally {
           lock.unlock();
        }
    }
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果是否为0
        int r = result.intValue();
        if(r != 0)
        {
            log.error("错误");
            // 2.1不为0代表没有购买资格
            return  Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
       //2.2为0有购买资格，加入到消息队列中
        long orderId = redisIdWorker.nextId("order");
        // TODO 保存阻塞队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        //创建阻塞队列
        orderTasks.add(voucherOrder);
        //获取代理对象(事务)
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        return Result.ok(orderId);
    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder)
    {
        //6.一人一单
        //扣减库存
        boolean success =  seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",voucherOrder.getVoucherId()).gt("stock",0).update();
        if(!success)
        {
            return;
        }
        save(voucherOrder);
    }
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询
//        SeckillVoucher byId = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始或者结束
//        if (byId.getBeginTime().isAfter(LocalDateTime.now())) {
//            //未开始
//            return Result.fail("秒杀尚未开始");
//        }
//        if (byId.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        //判断库存是否充足
//        if(byId.getStock() < 1)
//        {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean islock = lock.tryLock();
//        //判断是否成功
//        if(!islock)
//        {
//            //失败则返回错误信息
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            //获取代理对象(事务)
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//           lock.unlock();
//        }
//    }
//    @Transactional
//    public Result createVoucherOrder(Long voucherId)
//    {
//        //6.一人一单
//        //6.1查询订单
//        Long userId = UserHolder.getUser().getId();
//        //6.2判断是否存在
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if(count > 0)
//        {
//            //用户已经购买过
//            return Result.fail("用户已经购买过一次了");
//        }
//        //扣减库存
//        boolean success =  seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",voucherId).gt("stock",0).update();
//        if(!success)
//        {
//            return Result.fail("库存不足!");
//        }
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单id
//        long id = redisIdWorker.nextId("order");
//        voucherOrder.setId(id);
//        voucherOrder.setUserId(userId);
//        //代金券id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        //返回订单id
//        return Result.ok(id);
//    }
}
