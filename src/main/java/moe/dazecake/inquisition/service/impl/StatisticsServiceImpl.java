package moe.dazecake.inquisition.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import moe.dazecake.inquisition.mapper.AccountMapper;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.service.intf.StatisticsService;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    @Resource
    AccountMapper accountMapper;

    @Override
    public Result<HashMap<String, Object>> getStatistics() {
        Result<HashMap<String, Object>> result = new Result<>();
        result.setData(new HashMap<>());

        var payedUserList = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .ge(AccountEntity::getExpireTime, LocalDateTime.now())
                .eq(AccountEntity::getDelete, 0));

        var newUserList = accountMapper.selectList(Wrappers.<AccountEntity>lambdaQuery()
                .ge(AccountEntity::getCreateTime, LocalDateTime.of(LocalDate.now(), LocalTime.MIN).plusHours(4))
                .lt(AccountEntity::getCreateTime, LocalDateTime.of(LocalDate.now(), LocalTime.MAX).plusHours(4))
                .ge(AccountEntity::getExpireTime, LocalDateTime.now())
                .eq(AccountEntity::getDelete, 0));

        result.getData().put("payedUserNum", payedUserList.size());
        result.getData().put("newUserNum", newUserList.size());

        return result.setCode(200)
                .setMsg("success");
    }
}
