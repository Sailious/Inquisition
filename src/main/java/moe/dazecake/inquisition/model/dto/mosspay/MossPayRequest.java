package moe.dazecake.inquisition.model.dto.mosspay;

import lombok.Data;

@Data
public class MossPayRequest {
    private String versionId;
    private String serviceId;
    private String channelId;
    private String requestTime;
    private String serviceSn;
    private String businessChannel;
    private String orderNo;
    private String totalAmount;
    private String merNo;
    private String payScene;
    private String accountType;
    private String orderEffTime;
    private String notifyUrl;
    private String subject;
    private String remark;
    private String inteRouting;
}
