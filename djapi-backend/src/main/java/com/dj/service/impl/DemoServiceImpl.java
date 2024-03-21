package com.dj.service.impl;

import com.djapicommon.service.DemoService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class DemoServiceImpl implements DemoService {
    @Override
    public String sayHello(String name) {
        return name;
    }

    @Override
    public String sayHello2(String name) {
        return name;
    }
}
