package com.hmdp.utils;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.One;
import org.apache.ibatis.annotations.Result;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private RBloomFilter<Object> BloomFilter;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate)
    {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit)
    {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit)
    {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis中
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    //缓存穿透
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit)
    {
        String key = keyPrefix + id;
        //1.从redis中查询店铺缓存
        String s = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(s))
        {
            //3.存在则直接返回
            return JSONUtil.toBean(s, type);
        }
        if(s != null)
        {
            return null;
        }
        //4.不存在则根据id查询数据库
        R r = dbFallback.apply(id);
        //5.如果数据库中不存在则返回错误
        if(r == null)
        {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在则写入redis,并设置超时时间
        this.set(key,r,time,unit);
        return r;
    }
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit)
    {
        String key = keyPrefix + id;
        //0.先查询布隆过滤器:
        if(!BloomFilter.contains(key))
        {
            log.info("布隆过滤器中不存在，key: " + key);
            return null;
        }
        //1.从redis中查询店铺缓存
        String s = stringRedisTemplate.opsForValue().get(key);
        //命中需要判断过期时间
        RedisData bean = JSONUtil.toBean(s, RedisData.class);
        JSONObject data =  (JSONObject)bean.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = bean.getExpireTime();
        //未过期直接返回
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            return r;
        }
        //已过期则缓存重建
        //1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean trylock = trylock(lockKey);
        if(trylock)
        {
            //TODO 成功则开启新线程执行重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R rl = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,rl,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
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
