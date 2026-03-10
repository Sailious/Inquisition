package moe.dazecake.inquisition.model.dto.mosspay;

import lombok.Data;

@Data
public class MossPayResponse {
    private String versionId;
    private String serviceId;
    private String channelId;
    private String responseTime;
    private String serviceSn;
    private String businessChannel;
    private String respCode;
    private String respDesc;
    private String orderNo;
    private String paySerial;
    private String logNo;
    private String subMchId;
    private String prepayId;
    private String payScene;
    private String sign;
}
