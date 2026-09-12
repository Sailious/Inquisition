package moe.dazecake.inquisition.service.impl;

import moe.dazecake.inquisition.model.dto.heartbeat.HeartBeatDTO;
import moe.dazecake.inquisition.service.intf.HeartBeatService;
import moe.dazecake.inquisition.utils.DynamicInfo;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;

@Service
public class HeartBeatServiceImpl implements HeartBeatService {

    @Resource
    DynamicInfo dynamicInfo;

    @Override
    public Result<String> postHeartBeat(HeartBeatDTO heartBeat) {
        Result<String> result = new Result<>();
        //状态更新
        dynamicInfo.getDeviceCounterMap().put(heartBeat.getDeviceToken(), 3);
        dynamicInfo.getDeviceStatusMap().put(heartBeat.getDeviceToken(), heartBeat.getStatus());


        if (dynamicInfo.getWaitUserList().isEmpty()) {
            result.setCode(200);
        } else {
            result.setCode(201);
        }

        //停机检查
        if (dynamicInfo.getHaltList().contains(heartBeat.getDeviceToken())) {
            result.setCode(500);
        }
        return result.setMsg("success");
    }

    @Override
    public Result<String> postHaltComplete(HeartBeatDTO heartBeat) {
        Result<String> result = new Result<>();

        //移除停机列表中的该设备。
        // 原先用 while + remove(Object)：remove 一次只删首个匹配，需循环调用，且非原子。
        // 改用 removeIf —— CopyOnWriteArrayList 的 removeIf 是原子操作
        //（加锁后一次性替换底层数组），可一次移除全部匹配项，也不会出现"删到一半"的中间状态
        dynamicInfo.getHaltList().removeIf(token -> Objects.equals(token, heartBeat.getDeviceToken()));

        return result.setCode(200).setMsg("success");
    }
}
