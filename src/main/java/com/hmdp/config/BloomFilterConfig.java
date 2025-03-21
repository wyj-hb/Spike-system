package com.hmdp.config;

import cn.hutool.bloomfilter.BloomFilter;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.BloomFliterName;

@Configuration
public class BloomFilterConfig {
    @Resource
    private RedissonClient redissonClient;
    @Bean
    public RBloomFilter<Object> bloomFilter()
    {
        long expectedInsertions = 100000L; // 预期元素数量
        double falseProbability = 0.01; // 容错率，也就是误报率
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(BloomFliterName);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        return bloomFilter;
    }
}
