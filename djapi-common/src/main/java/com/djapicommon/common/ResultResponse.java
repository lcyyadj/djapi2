package com.djapicommon.common;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class ResultResponse implements Serializable {
    private static final long serialVersionUID = -6486005224268968744L;
    private Map<String, Object> data = new HashMap();
}
