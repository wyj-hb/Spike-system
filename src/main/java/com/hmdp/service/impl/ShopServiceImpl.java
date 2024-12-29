package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryByid(Long id) {
        //1.从redis中查询店铺缓存
        String s = stringRedisTemplate.opsForValue().get("cache:shop:" + id);
        //2.判断是否存在
        if(StrUtil.isNotBlank(s))
        {
            //3.存在则直接返回
            Shop shop = JSONUtil.toBean(s, Shop.class);
            return Result.ok(shop);
        }
        //4.不存在则根据id查询数据库
        Shop byId = getById(id);
        //5.如果数据库中不存在则返回错误
        if(byId == null)
        {
            return Result.fail("店铺不存在");
        }
        //6.存在则写入redis
        stringRedisTemplate.opsForValue().set("cache:shop:" + id,JSONUtil.toJsonStr(byId));
        return Result.ok(byId);
    }
}
