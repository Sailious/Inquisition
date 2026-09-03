package moe.dazecake.inquisition;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import moe.dazecake.inquisition.service.impl.EmailServiceImpl;
import moe.dazecake.inquisition.service.impl.QmsgServiceImpl;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Email / Qmsg 推送服务的纯 mock 单元测试。
 *
 * <p>不加载 Spring 完整上下文（无 DB 依赖），不真实外发邮件或 QQ 推送。
 * 通过 Mockito 校验 service 是否正确组装消息并调用底层客户端。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private OkHttpClient okHttpClient;

    @Mock
    private Call call;

    private EmailServiceImpl emailService;

    private QmsgServiceImpl qmsgService;

    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() throws Exception {
        emailService = new EmailServiceImpl();
        setField(emailService, "mailSender", mailSender);
        setField(emailService, "from", "noreply@example.com");
        setField(emailService, "nickname", "阿戈尔科技");

        qmsgService = new QmsgServiceImpl();
        setField(qmsgService, "enableQmsg", true);
        setField(qmsgService, "qmsgKey", "test-qmsg-key");
        setField(qmsgService, "client", okHttpClient);
    }

    /**
     * 通过反射注入 private 字段，避免 Spring 上下文依赖。
     */
    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 从 RequestBody 读取字符串内容。
     */
    private static String bodyToString(RequestBody requestBody) throws Exception {
        Buffer buffer = new Buffer();
        requestBody.writeTo(buffer);
        return buffer.readUtf8();
    }

    @Test
    @DisplayName("sendSimpleMail 应构建正确消息并交给 JavaMailSender 发送")
    void sendSimpleMail_ShouldBuildAndSendMessage() {
        emailService.sendSimpleMail("receiver@example.com", "Test Subject", "Hello Content");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        SimpleMailMessage msg = captor.getValue();
        assertEquals("阿戈尔科技<noreply@example.com>", msg.getFrom());
        assertEquals("receiver@example.com", msg.getTo()[0]);
        assertEquals("Test Subject", msg.getSubject());
        assertEquals("Hello Content", msg.getText());
    }

    @Test
    @DisplayName("Qmsg 启用且配置 key 时应构建正确请求体并发送")
    void qmsgPush_WhenEnabled_ShouldSendRequestWithCorrectBody() throws Exception {
        // 构造真实 Response，避免 mock final 类 okhttp3.Response
        Request dummy = new Request.Builder()
                .url("https://dummy.example.com")
                .build();
        ResponseBody body = ResponseBody.create(
                "{\"success\":true}",
                MediaType.get("application/json; charset=utf-8")
        );
        Response realResponse = new Response.Builder()
                .request(dummy)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build();

        when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
        when(call.execute()).thenReturn(realResponse);

        qmsgService.push("1097561282", "【测试消息】");

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(okHttpClient, times(1)).newCall(requestCaptor.capture());

        Request actualRequest = requestCaptor.getValue();

        // 断言 URL
        String url = actualRequest.url().toString();
        assertEquals("https://qmsg.zendee.cn/jsend/test-qmsg-key", url);

        // 断言请求体：{ "msg": "...", "qq": "..." }
        RequestBody requestBody = actualRequest.body();
        assertEquals(QmsgServiceImpl.JSON, requestBody.contentType());

        String bodyContent = bodyToString(requestBody);
        JsonObject json = gson.fromJson(bodyContent, JsonObject.class);
        assertEquals("【测试消息】", json.get("msg").getAsString());
        assertEquals("1097561282", json.get("qq").getAsString());

        // 断言网络调用
        verify(call, times(1)).execute();
    }

    @Test
    @DisplayName("Qmsg 未启用时不应发起网络请求")
    void qmsgPush_WhenDisabled_ShouldSkipRequest() throws Exception {
        setField(qmsgService, "enableQmsg", false);

        qmsgService.push("1097561282", "test");

        verify(okHttpClient, never()).newCall(any(Request.class));
    }

    @Test
    @DisplayName("Qmsg 未配置 key 时不应发起网络请求")
    void qmsgPush_WhenKeyEmpty_ShouldSkipRequest() throws Exception {
        setField(qmsgService, "qmsgKey", "");

        qmsgService.push("1097561282", "test");

        verify(okHttpClient, never()).newCall(any(Request.class));
    }

    @Test
    @DisplayName("Qmsg key 为 null 时不应发起网络请求")
    void qmsgPush_WhenKeyNull_ShouldSkipRequest() throws Exception {
        setField(qmsgService, "qmsgKey", null);

        qmsgService.push("1097561282", "test");

        verify(okHttpClient, never()).newCall(any(Request.class));
    }
}
