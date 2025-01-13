package com.hmdp.config;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RedissionConfig
{
    @Bean
    public RedissonClient redissionClient()
    {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.43.11:6379").setPassword("123456");
        return Redisson.create(config);
    }
}
