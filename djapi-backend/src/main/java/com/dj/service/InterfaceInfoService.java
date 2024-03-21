package com.dj.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.djapicommon.model.entity.InterfaceInfo;

/**
* @author len'len
* @description 针对表【interface_info(接口信息)】的数据库操作Service
* @createDate 2024-02-29 23:48:56
*/
public interface InterfaceInfoService extends IService<InterfaceInfo> {

    void validInterfaceInfo(InterfaceInfo interfaceInfo, boolean b);

    void invokeCount(long id);
}
