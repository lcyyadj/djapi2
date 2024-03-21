package com.djapicommon.service.inner;


import com.djapicommon.model.vo.UserVO;

public interface InnerUserService {

    UserVO getInvokeUserByAccessKey(String accessKey);
}
