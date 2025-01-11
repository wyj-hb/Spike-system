package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements Ilock
{
    private String name;
    private StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate redisTemplate) {
        this.name = name;
        this.redisTemplate = redisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, ThreadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {
        //获取线程标识
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        //判断标识是否一致
        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(ThreadId.equals(id))
        {
            redisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
