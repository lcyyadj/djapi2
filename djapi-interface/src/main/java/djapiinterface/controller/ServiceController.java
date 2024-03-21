package djapiinterface.controller;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.dj.djapiclientsdk.model.request.ExecuteCodeRequest;
import com.djapicommon.common.ApiException;
import com.djapicommon.common.RandomWallpaperParams;
import com.djapicommon.common.RandomWallpaperResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;

import static djapiinterface.utils.RequestUtils.*;


@RestController
@RequestMapping("/")
public class ServiceController {
    @GetMapping("/name")
    public String getName(String name) {
        return name;
    }

    @GetMapping("/loveTalk")
    public String randomLoveTalk() {
        return get("https://api.vvhan.com/api/love");
    }

    @GetMapping("/poisonousChickenSoup")
    public String getPoisonousChickenSoup() {
        return get("https://api.btstu.cn/yan/api.php?charset=utf-8&encode=json");
    }

    @GetMapping("/randomWallpaper")
    public RandomWallpaperResponse randomWallpaper(RandomWallpaperParams randomWallpaperParams) throws ApiException {
        String baseUrl = "https://api.btstu.cn/sjbz/api.php";
        String url = buildUrl(baseUrl, randomWallpaperParams);
        if (StringUtils.isAllBlank(randomWallpaperParams.getLx(), randomWallpaperParams.getMethod())) {
            url = url + "?format=json";
        } else {
            url = url + "&format=json";
        }
        return JSONUtil.toBean(get(url), RandomWallpaperResponse.class);
    }
    @PostMapping("/judgeQuestion")
    public String getJudgeQuestion(@RequestBody String code) {
        ExecuteCodeRequest executeCodeRequest=new ExecuteCodeRequest();
        JSONObject obj = JSONUtil.parseObj(code);
        executeCodeRequest.setCode(obj.get("code",String.class));
        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));
        String body = HttpUtil.createPost("http://localhost:8090/executeCode")
                .header("auth", "secret")
                .body(JSONUtil.toJsonStr(executeCodeRequest))
                .execute()
                .body();
//        post("http://localhost:8090/judge/executeCode",code);
        HashMap<String,String>temp=new HashMap<>();
        temp.put("text",body);
        return JSONUtil.toJsonPrettyStr(temp);
    }

}
