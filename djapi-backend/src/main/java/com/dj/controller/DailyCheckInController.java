package com.dj.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dj.common.BaseResponse;
import com.dj.common.ErrorCode;
import com.dj.common.ResultUtils;
import com.dj.exception.BusinessException;
import com.dj.model.entity.DailyCheckIn;
import com.dj.service.DailyCheckInService;
import com.dj.service.UserService;
import com.dj.utils.RedissonLockUtil;
import com.djapicommon.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/dailyCheckIn")
@Slf4j
public class DailyCheckInController {

    @Resource
    private DailyCheckInService dailyCheckInService;

    @Resource
    private UserService userService;
    @Resource
    private RedissonLockUtil redissonLockUtil;

    

    @PostMapping("/doCheckIn")
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Boolean> doDailyCheckIn(HttpServletRequest request) {
        UserVO loginUser = new UserVO();
        BeanUtils.copyProperties(userService.getLoginUser(request),loginUser);
        String redissonLock = ("doDailyCheckIn_" + loginUser.getUserAccount()).intern();
        return redissonLockUtil.redissonDistributedLocks(redissonLock, () -> {
            LambdaQueryWrapper<DailyCheckIn> dailyCheckInLambdaQueryWrapper = new LambdaQueryWrapper<>();
            dailyCheckInLambdaQueryWrapper.eq(DailyCheckIn::getUserId, loginUser.getId());
            DailyCheckIn dailyCheckIn = dailyCheckInService.getOne(dailyCheckInLambdaQueryWrapper);
            if (ObjectUtils.isNotEmpty(dailyCheckIn)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "签到失败,今日已签到");
            }
            dailyCheckIn = new DailyCheckIn();
            dailyCheckIn.setUserId(loginUser.getId());
            dailyCheckIn.setAddPoints(10);
            boolean dailyCheckInResult = dailyCheckInService.save(dailyCheckIn);
            boolean addWalletBalance = userService.addWalletPoints(loginUser.getId(), dailyCheckIn.getAddPoints());
            boolean result = dailyCheckInResult & addWalletBalance;
            if (!result) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR);
            }
            return ResultUtils.success(true);
        }, "签到失败");
    }
    
}
