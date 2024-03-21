package com.djapicommon.service.inner;


import com.djapicommon.model.entity.InterfaceInfo;

public interface InnerInterfaceInfoService {
    InterfaceInfo getInterfaceInfo(String path, String method);
}
