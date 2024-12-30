package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryByid(Long id) {
        //缓存穿透
//        Shop shop = queryWithPassThrough(id);
        //使用互斥锁解决缓冲击穿
        Shop shop = queryWithMutex(id);
        if(shop == null)
        {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    public Shop queryWithMutex(Long id)
    {
        //1.从redis中查询店铺缓存
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if(StrUtil.isNotBlank(s))
        {
            //3.存在则直接返回
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return shop;
        }
        if(s != null)
        {
            return null;
        }
        //4.实现缓存重建
        //4.1 获取互斥锁
        Shop byId = null;
        String lockKey = "lock:shop:" + id;
        try {
            boolean islock = trylock(lockKey);
            //4.2判断是否获取成功
            if(!islock)
            {
                //4.3失败,则失眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.获得锁,根据id查询数据库
            byId = getById(id);
            //模拟重建延迟
            Thread.sleep(200);
            //5.如果数据库中不存在则返回错误
            if(byId == null)
            {
                stringRedisTemplate.opsForValue().set("cache:shop:" + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在则写入redis,并设置超时时间
            stringRedisTemplate.opsForValue().set("cache:shop:" + id,JSONUtil.toJsonStr(byId),30L, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unlock(lockKey);
        }
        return byId;
    }

    public Shop queryWithPassThrough(Long id)
    {
        //1.从redis中查询店铺缓存
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if(StrUtil.isNotBlank(s))
        {
            //3.存在则直接返回
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return shop;
        }
        if(s != null)
        {
            return null;
        }
        //4.不存在则根据id查询数据库
        Shop byId = getById(id);
        //5.如果数据库中不存在则返回错误
        if(byId == null)
        {
            stringRedisTemplate.opsForValue().set("cache:shop:" + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在则写入redis,并设置超时时间
        stringRedisTemplate.opsForValue().set("cache:shop:" + id,JSONUtil.toJsonStr(byId),30L, TimeUnit.MINUTES);
        return byId;
    }
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null)
        {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
    private boolean trylock(String key)
    {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }
    private void unlock(String key)
    {
        stringRedisTemplate.delete(key);
    }
}
