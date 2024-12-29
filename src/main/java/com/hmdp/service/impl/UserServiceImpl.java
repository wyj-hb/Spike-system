package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private UserMapper userService;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone))
        {
            //2.如果不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合,生产验证码
        String s = RandomUtil.randomNumbers(6);
        //4.保存验证码到session
        session.setAttribute("code",s);
        //5.发送验证码
        log.debug("发送短信验证码成功,验证码为：{}",s);
        return Result.ok();
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone()))
        {
            //2.如果不符合,返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2.校验验证码
        String code = loginForm.getCode();
        if(code == null || !session.getAttribute("code").equals(code))
        {
            return Result.fail("验证码错误");
        }
        //3.查询手机号对应的用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        if(user == null)
        {
            //4.创建用户
            user = createUserWithPhone(loginForm.getPhone());
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        //5.保存用户到session中
        session.setAttribute("user",userDTO);
        return Result.ok();
    }
    private User createUserWithPhone(String phone) {
        //生成手机号和随机用户名
        User build = User.builder().phone(phone).nickName("user_" + RandomUtil.randomString(10)).build();
        //保存
        save(build);
        return build;
    }
}
