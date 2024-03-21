package com.dj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dj.common.ErrorCode;
import com.dj.exception.BusinessException;
import com.dj.mapper.UserInterfaceInfoMapper;
import com.dj.service.InterfaceInfoService;
import com.dj.service.UserInterfaceInfoService;
import com.dj.service.UserService;
import com.djapicommon.model.entity.UserInterfaceInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
* @author len'len
* @description 针对表【user_interface_info(用户调用接口关系)】的数据库操作Service实现
* @createDate 2024-03-04 14:45:30
*/
@Service
public class UserInterfaceInfoServiceImpl extends ServiceImpl<UserInterfaceInfoMapper, UserInterfaceInfo>
    implements UserInterfaceInfoService {
    @Resource
    private UserService userService;
    @Resource
    private InterfaceInfoService interfaceInfoService;
    @Override
    public void validUserInterfaceInfo(UserInterfaceInfo userInterfaceInfo, boolean add) {
        if (userInterfaceInfo == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 创建时，所有参数必须非空
        if (add) {
            if (userInterfaceInfo.getInterfaceInfoId() <= 0 || userInterfaceInfo.getUserId() <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "接口或用户不存在");
            }
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean invokeCount(long interfaceInfoId, long userId) {
        if (interfaceInfoId <= 0 || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 判断,集中处理
        //处理用户接口表
        UserInterfaceInfo userInterfaceInfo=this.getOne(new QueryWrapper<UserInterfaceInfo>().eq("userId",userId).eq("interfaceInfoId",interfaceInfoId));
        if(userInterfaceInfo==null){
            UserInterfaceInfo userInterfaceInfo1 = new UserInterfaceInfo();
            userInterfaceInfo1.setUserId(userId);
            userInterfaceInfo1.setInterfaceInfoId(interfaceInfoId);
            this.save(userInterfaceInfo1);
        }
        UpdateWrapper<UserInterfaceInfo> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("interfaceInfoId", interfaceInfoId);
        updateWrapper.eq("userId", userId);
//        updateWrapper.gt("leftNum", 0);
        updateWrapper.setSql("totalNum = totalNum + 1");
        //处理用户表
        userService.invokeCount(userId);
        //处理接口表
        interfaceInfoService.invokeCount(interfaceInfoId);
        return this.update(updateWrapper);
    }
}




