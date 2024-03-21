package djapiinterface.utils;

import com.djapicommon.common.ApiException;
import com.djapicommon.common.ErrorCode;
import com.djapicommon.common.ResultResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

import static djapiinterface.utils.RequestUtils.*;

public class ResponseUtils {
    public static Map<String, Object> responseToMap(String response) {
        return new Gson().fromJson(response, new TypeToken<Map<String, Object>>() {
        }.getType());
    }

    public static <T> ResultResponse baseResponse(String baseUrl, T params) throws ApiException {
        String response = null;
        try {
            response = get(baseUrl, params);
            Map<String, Object> fromResponse = responseToMap(response);
            boolean success = (boolean) fromResponse.get("success");
            ResultResponse baseResponse = new ResultResponse();
            if (!success) {
                baseResponse.setData(fromResponse);
                return baseResponse;
            }
            fromResponse.remove("success");
            baseResponse.setData(fromResponse);
            return baseResponse;
        } catch (ApiException e) {
            throw new ApiException(ErrorCode.OPERATION_ERROR, "构建url异常");
        }
    }
}
