package com.dj.service.impl.inner;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dj.common.ErrorCode;
import com.dj.exception.BusinessException;
import com.dj.mapper.UserMapper;
import com.djapicommon.model.entity.User;
import com.djapicommon.model.vo.UserVO;
import com.djapicommon.service.inner.InnerUserService;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;

import javax.annotation.Resource;

/**
 * 内部用户服务实现类
 */
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public UserVO getInvokeUserByAccessKey(String accessKey) {
        if (StringUtils.isAnyBlank(accessKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("accessKey", accessKey);
        UserVO userVO=new UserVO();
        BeanUtils.copyProperties(userMapper.selectOne(queryWrapper),userVO);
        return userVO;
    }
}
