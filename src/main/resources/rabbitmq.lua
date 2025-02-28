---
--- Generated by Luanalysis
--- Created by wangyujie.
--- DateTime: 2025-01-13 16:54
---
--- 判断库存是否充足
--- 优惠券id
local voucherId = ARGV[1]
--- 用户id
local userId = ARGV[2]
--- 数据key
--- 库存key
local stockKey = 'seckill:stock:' .. voucherId
--- 订单id
local orderKey = 'seckill:order:' .. voucherId
--- 业务脚本
--- 判断库存是否重复
if(tonumber(redis.call('get',stockKey)) <= 0) then
    --- 库存不足则返回1
    return 1
end
--- 判断用户是否下单
if(redis.call('sismember',orderKey,userId) == 1) then
    --- 存在这说明是重复下单，返回2
    return 2
end
--- 扣库存
redis.call("incrby",stockKey,-1)
--- 下单
redis.call('sadd',orderKey,userId)
return 0