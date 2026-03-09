package moe.dazecake.inquisition.service.impl;

import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.service.intf.QmsgService;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Objects;

@Slf4j
@Service
public class QmsgServiceImpl implements QmsgService {

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Value("${qmsg.enable:false}")
    private boolean enableQmsg;

    @Value("${qmsg.key:}")
    private String qmsgKey;

    OkHttpClient client = new OkHttpClient();

    Gson gson = new Gson();

    @SneakyThrows
    @Override
    public void push(String qq, String content) {
        if (!enableQmsg || qmsgKey == null || qmsgKey.isEmpty()) {
            log.warn("【审判庭】 Qmsg推送未启用或未配置key");
            return;
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("msg", content);
        params.put("qq", qq);

        Request request = new Request.Builder()
                .url("https://qmsg.zendee.cn/jsend/" + qmsgKey)
                .post(RequestBody.create(gson.toJson(params), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            var responseBody = Objects.requireNonNull(response.body()).string();
            log.info("【审判庭】 Qmsg推送结果: " + responseBody);
        } catch (Exception e) {
            log.error("【审判庭】 Qmsg推送失败", e);
        }
    }
}
