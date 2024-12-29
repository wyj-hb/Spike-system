package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson.JSONObject;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.logging.log4j.message.MapMessage.MapFormat.JSON;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> RedisQuery() {
        //首先查询redis缓存
        List<String> l = stringRedisTemplate.opsForList().range("shop:listkey", 0, -1);
        //在缓存中则直接返回
        if(!l.isEmpty())
        {
            //返回数据
            List<ShopType> collect = l.stream().map(str -> JSONObject.parseObject(str, ShopType.class)).collect(Collectors.toList());
            return collect;
        }
        //不在则查询数据库,并将结果记录到Redis缓存中
        List<ShopType> sort = query().orderByAsc("sort").list();
        for(ShopType s : sort)
        {
            stringRedisTemplate.opsForList().rightPush("shop:listkey",JSONObject.toJSONString(s));
        }
        //返回结果
        return sort;
    }
}
