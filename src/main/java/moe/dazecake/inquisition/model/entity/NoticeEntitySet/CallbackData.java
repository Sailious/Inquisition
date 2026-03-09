package moe.dazecake.inquisition.model.entity.NoticeEntitySet;

import lombok.Data;

@Data
public class CallbackData {
    private Long appId;
    private String appKey;
    private String appName;
    private String source;
    private String userName;
    private String userHeadImg;
    private Long time;
    private String uid;
    private String extra;
}