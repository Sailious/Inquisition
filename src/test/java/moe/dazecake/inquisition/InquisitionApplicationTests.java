package moe.dazecake.inquisition;

import moe.dazecake.inquisition.service.impl.EmailServiceImpl;
import moe.dazecake.inquisition.service.impl.QmsgServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@SpringBootTest(classes = InquisitionApplication.class)
@RunWith(SpringRunner.class)
class InquisitionApplicationTests {

    @Resource
    EmailServiceImpl emailService;

    @Resource
    QmsgServiceImpl qmsgService;

    @Test
    void testMail() {
        System.out.println("测试邮件");
        emailService.sendSimpleMail("1936260102@qq.com", "test", "test");
        System.out.println("测试邮件over");
    }

    @Test
    void testQmsg() {
        System.out.println("测试QQ推送");
        qmsgService.push("1097561282", "【测试消息】\n\n这是一条来自Inquisition项目的测试消息！");
        System.out.println("测试QQ推送over");
    }
}
