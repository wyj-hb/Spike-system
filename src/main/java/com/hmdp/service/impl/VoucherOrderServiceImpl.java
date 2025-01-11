package com.hmdp.service.impl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.LocalDateTime;
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询
        SeckillVoucher byId = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始或者结束
        if (byId.getBeginTime().isAfter(LocalDateTime.now())) {
            //未开始
            return Result.fail("秒杀尚未开始");
        }
        if (byId.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if(byId.getStock() < 1)
        {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean islock = lock.tryLock(1200);
        //判断是否成功
        if(!islock)
        {
            //失败则返回错误信息
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象(事务)
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
           lock.unlock();
        }
    }
    @Transactional
    public Result createVoucherOrder(Long voucherId)
    {
        //6.一人一单
        //6.1查询订单
        Long userId = UserHolder.getUser().getId();
        //6.2判断是否存在
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0)
        {
            //用户已经购买过
            return Result.fail("用户已经购买过一次了");
        }
        //扣减库存
        boolean success =  seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",voucherId).gt("stock",0).update();
        if(!success)
        {
            return Result.fail("库存不足!");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long id = redisIdWorker.nextId("order");
        voucherOrder.setId(id);
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //返回订单id
        return Result.ok(id);
    }
}
