package com.hmdp.service.impl;

import ch.qos.logback.core.joran.action.AppenderAction;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }
    private void isBlogLiked(Blog blog)
    {
        //1.获取登录用户
        Long UserId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞
        String key = "blog:liked:" + blog.getId();
        Boolean panduan = stringRedisTemplate.opsForSet().isMember(key, UserId.toString());
        blog.setIsLike(BooleanUtil.isTrue(panduan));
    }
    @Override
    public Result queryByid(Long id) {
        Blog blog = getById(id);
        if(blog == null)
        {
            return Result.fail("笔记不存在!");
        }
        //查询blog有关的用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long UserId = UserHolder.getUser().getId();
        //2.判断当前用户是否已经点赞
        String key = "blog:liked:" + id;
        Boolean panduan = stringRedisTemplate.opsForSet().isMember(key, UserId.toString());
        if(BooleanUtil.isFalse(panduan))
        {
            //3.如果未点赞,可以点赞
            //3.1 数据库点赞数+1
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2 保存到Redis的set集合中
            if(success)
            {
                //更新redis
                stringRedisTemplate.opsForSet().add(key,UserId.toString());
            }
        }
        else
        {
            //4.如果已点赞则取消点赞
            //4.1数据库点赞数-1
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2把用户从Redis的set集合中移除
            stringRedisTemplate.opsForSet().remove(key,UserId.toString());
        }
        return Result.ok();
    }
}
