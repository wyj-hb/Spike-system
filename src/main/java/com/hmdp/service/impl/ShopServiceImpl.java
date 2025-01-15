package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryByid(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,this::getById,20L ,TimeUnit.SECONDS);
        //使用互斥锁解决缓冲击穿
//        Shop shop = queryWithMutex(id);
        //使用逻辑过期来解决
//        Shop shop = queryWithLogicalExpire(id);
        if(shop == null)
        {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id)
    {
        //1.从redis中查询店铺缓存
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if(StrUtil.isBlank(s))
        {
            //3.存在则直接返回
            return null;
        }
        //命中需要判断过期时间
        RedisData bean = JSONUtil.toBean(s, RedisData.class);
        JSONObject data =  (JSONObject)bean.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = bean.getExpireTime();
        //未过期直接返回
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            return shop;
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
                    this.saveShop2Redis(id,30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return shop;
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
    public void saveShop2Redis(Long id,Long expireSeconds)
    {
        //1.查询店铺数据
        Shop shop = getById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要根据坐标查询
        if(x == null || y == null)
        {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = (current) * SystemConstants.DEFAULT_PAGE_SIZE;
        //查询redis,按照距离排序、分页 结果:shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        //解析出id
        if(results == null)
        {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if(content.size() <= from)
        {
            return Result.ok(Collections.emptyList());
        }
        //截取从from 到 end
        List<Long> ids = new ArrayList<>(content.size());
        Map<String,Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(from).forEach(result -> {
            //获取店铺id
            String shopId = result.getContent().getName();
            ids.add(Long.valueOf(shopId));
            //获取距离
            Distance dis = result.getDistance();
            distanceMap.put(shopId,dis);
        });
        //根据id查询Shop
        String idStr = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for(Shop shop : shops)
        {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //返回
        return Result.ok(shops);
    }
}
