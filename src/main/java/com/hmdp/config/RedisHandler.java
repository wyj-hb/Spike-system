package com.hmdp.config;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
@Component
public class RedisHandler implements InitializingBean {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IShopService ShopService;
    private static final ObjectMapper mapper = new ObjectMapper();
    @Override
    public void afterPropertiesSet() throws Exception {
        //初始化缓存
        //将所有查询到的店铺信息记录到Redis缓存中
        List<Shop> shopList = ShopService.list();
        Long time = 20L ;
        TimeUnit unit = TimeUnit.SECONDS;
        for (Shop item : shopList) {
            //封装数据
            RedisData redisData = new RedisData();
            redisData.setData(item);
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
            stringRedisTemplate.opsForValue().set("cache:shop:" + item.getId(), JSONUtil.toJsonStr(item));
        }
    }
}
