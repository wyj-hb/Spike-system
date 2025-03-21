package com.hmdp.config;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
@Slf4j
@Component
public class RedisHandler implements InitializingBean {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private IShopService ShopService;
    private static final ObjectMapper mapper = new ObjectMapper();
    @Autowired
    private RBloomFilter<Object> bloomFilter;
    @Override
    public void afterPropertiesSet() throws Exception {
        //初始化缓存
        //将所有查询到的店铺信息记录到Redis缓存中
        List<Shop> shopList = ShopService.list();
        TimeUnit unit = TimeUnit.SECONDS;
        for (Shop item : shopList) {
            //封装数据
            RedisData redisData = new RedisData();
            redisData.setData(item);
            //设置随机TTL防止缓存雪崩
            long ttl = CACHE_SHOP_TTL + ThreadLocalRandom.current().nextInt(-5, 6);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(ttl)));
            String key = "cache:shop:" + item.getId();
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
            bloomFilter.add(key);
        }
        //将秒杀优惠券信息保存到Redis中
        List<SeckillVoucher> v = seckillVoucherService.list();
        for(SeckillVoucher voucher : v){
            String key = SECKILL_STOCK_KEY + voucher.getVoucherId();
            //保存秒杀库存到Redis中
            stringRedisTemplate.opsForHash().put(key,"stock",voucher.getStock().toString());
            String beginTime = String.valueOf(voucher.getBeginTime().toEpochSecond(ZoneOffset.of("+8")));
            String endTime = String.valueOf(voucher.getEndTime().toEpochSecond(ZoneOffset.of("+8")));
            stringRedisTemplate.opsForHash().put(key,"begin_time",beginTime);
            stringRedisTemplate.opsForHash().put(key,"end_time",endTime);
            log.info("存入秒杀券信息: " + voucher.getVoucherId());
        }
    }
}
