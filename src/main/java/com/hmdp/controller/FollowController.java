package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService iFollowService;
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id,@PathVariable("isFollow") Boolean isFollow)
    {
        return iFollowService.follow(id,isFollow);
    }
    @PutMapping("/or/not/{id}")
    public Result followOrNot(@PathVariable("id") Long id)
    {
        return iFollowService.followOrNot(id);
    }
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable("id") Long id)
    {
        return iFollowService.followCommons(id);
    }
}
