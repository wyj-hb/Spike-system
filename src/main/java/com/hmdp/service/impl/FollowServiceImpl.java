package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService iUserService;
    @Override
    public Result follow(Long id, Boolean isFollow) {
        Long UserId = UserHolder.getUser().getId();
        //把用户的关注加入到关注列表中
        String key = "follows:" + UserId;
        //判断关注还是取关
        if(isFollow)
        {
            //关注则新增数据
            Follow follow = new Follow();
            follow.setUserId(UserId);
            follow.setFollowUserId(id);
            boolean save = save(follow);
            if(save)
            {

                stringRedisTemplate.opsForSet().add(key,id.toString());
            }
        }
        //取关则删除
        else
        {
            boolean sucess = remove(new QueryWrapper<Follow>().eq("user_id", UserId).eq("follow_user_id", id));
            //移除
            if(sucess)
            {
                stringRedisTemplate.opsForSet().remove(key,id.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followOrNot(Long id) {
        Long UserId = UserHolder.getUser().getId();
        //查询是否关注
        Integer count = query().eq("user_id", UserId).eq("follow_user_id", id).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户
        Long UserId = UserHolder.getUser().getId();
        String key1 = "follows:" + UserId;
        //求取交集
        String key2 = "follows:" + id;
        Set<String> res = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(res == null || res.isEmpty())
        {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = res.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = iUserService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
