package com.dj.model.dto.interfaceInfo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class InvokeRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    private Long id;
    private String userRequestParams;

    @Data
    public static class Field {
        private String fieldName;
        private String value;
    }
}

