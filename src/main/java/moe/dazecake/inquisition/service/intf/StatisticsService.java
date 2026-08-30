package moe.dazecake.inquisition.service.intf;

import moe.dazecake.inquisition.utils.Result;

import java.util.HashMap;

public interface StatisticsService {

    /**
     * 获取概览统计数据
     *
     * @return: Result<HashMap < String, Object>>
     * @author DazeCake
     * @date 2023/1/26 23:55
     */
    Result<HashMap<String, Object>> getStatistics();

}
