package moe.dazecake.inquisition.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import moe.dazecake.inquisition.mapper.AccountMapper;
import moe.dazecake.inquisition.mapper.DeviceMapper;
import moe.dazecake.inquisition.mapper.LogMapper;
import moe.dazecake.inquisition.mapper.mapstruct.LogConvert;
import moe.dazecake.inquisition.model.dto.log.AddImageDTO;
import moe.dazecake.inquisition.model.dto.log.AddLogDTO;
import moe.dazecake.inquisition.model.dto.log.LogDTO;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.model.entity.DeviceEntity;
import moe.dazecake.inquisition.model.entity.LogEntity;
import moe.dazecake.inquisition.model.vo.query.PageQueryVO;
import moe.dazecake.inquisition.service.intf.LogService;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Service
public class LogServiceImpl implements LogService {

    @Resource
    LogMapper logMapper;

    @Resource
    AccountMapper accountMapper;

    @Resource
    MessageServiceImpl messageService;

    @Resource
    DeviceMapper deviceMapper;

    @Resource
    ImageServiceImpl imageService;

    @Override
    public void addLog(AddLogDTO addLogDTO, boolean isSystem) {
        var logEntity = LogConvert.INSTANCE.toLogEntity(addLogDTO);
        logEntity.setId(0L);
        logEntity.setTime(LocalDateTime.now());
        logEntity.setDelete(0);
        // 安全修复：日志不落库明文密码，防止敏感信息泄露。
        // 日志中的账号密码仅用于设备侧实时排障，入库时清除。
        logEntity.setPassword(null);
        if (isSystem) {
            logEntity.setTaskType("SYSTEM");
            logEntity.setFrom("SYSTEM");
        } else {
            specialScan(addLogDTO);
            //去除 "hikay960q4 "
            if (logEntity.getDetail() != null) {
                logEntity.setDetail(logEntity.getDetail().replace("hikay960q4 ", ""));
            }
        }
        // H3 修复：对日志文本做HTML转义，防止存储型XSS
        // 注意：imageUrl 不转义 —— COS 预签名URL含 & 查询参数，转义会导致签名失效、前端图片加载失败
        if (logEntity.getDetail() != null) {
            logEntity.setDetail(escapeHtml(logEntity.getDetail()));
        }
        if (logEntity.getTitle() != null) {
            logEntity.setTitle(escapeHtml(logEntity.getTitle()));
        }
        logMapper.insert(logEntity);
    }

    /**
     * H3 修复：HTML 实体转义，防止存储型 XSS。
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Override
    public Result<String> uploadImage(AddImageDTO addImageDTO) {
        // 安全修复：同时校验设备未被逻辑删除，防止已删除设备继续上传文件
        var device = deviceMapper.selectOne(Wrappers.<DeviceEntity>lambdaQuery()
                .eq(DeviceEntity::getDeviceToken, addImageDTO.getDeviceToken())
                .eq(DeviceEntity::getDelete, 0));
        if (device == null) {
            return Result.notFound("设备不存在");
        }
        return imageService.uploadImage(addImageDTO.getBase64Image());
    }

    @Override
    public void logInfo(String title, String detail) {
        var addLogDTO = new AddLogDTO();
        addLogDTO.setLevel("INFO");
        addLogDTO.setTitle(title);
        addLogDTO.setDetail(detail);
        addLog(addLogDTO, true);
    }

    @Override
    public void logWarn(String title, String detail) {
        var addLogDTO = new AddLogDTO();
        addLogDTO.setLevel("WARN");
        addLogDTO.setTitle(title);
        addLogDTO.setDetail(detail);
        addLog(addLogDTO, true);
    }

    @Override
    public void specialScan(AddLogDTO addLogDTO) {
        if (addLogDTO.getDetail() != null && addLogDTO.getDetail().contains("高级资深干员")) {
            try {
                var luckyDog = accountMapper.selectOne(Wrappers.<AccountEntity>lambdaQuery()
                        .eq(AccountEntity::getAccount, addLogDTO.getAccount()));
                messageService.push(luckyDog, "高级资深干员提示", "恭喜你获得了高级资深干员！快上游戏看看吧！");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deleteLog(Long id) {
        var logEntity = logMapper.selectById(id);
        if (logEntity != null) {
            logEntity.setDelete(1);
            logMapper.updateById(logEntity);
        }
    }

    @Override
    public PageQueryVO<LogDTO> queryAllLog(Long current, Long size) {
        //降序分页查找（仅返回未删除的日志）
        var data = logMapper.selectPage(new Page<>(current, size), Wrappers.<LogEntity>lambdaQuery()
                .eq(LogEntity::getDelete, 0)
                .orderByDesc(LogEntity::getId));
        return getLogPageQueryVO(data);
    }

    @Override
    public PageQueryVO<LogDTO> queryLogByAccount(String account, Long current, Long size) {
        var data = logMapper.selectPage(new Page<>(current, size), Wrappers.<LogEntity>lambdaQuery()
                .eq(LogEntity::getAccount, account)
                .eq(LogEntity::getDelete, 0)
                .orderByDesc(LogEntity::getId));
        return getLogPageQueryVO(data);
    }

    @NotNull
    public PageQueryVO<LogDTO> getLogPageQueryVO(Page<LogEntity> data) {
        var result = new PageQueryVO<LogDTO>();
        result.setCurrent(data.getCurrent());
        result.setPage(data.getPages());
        result.setTotal(data.getTotal());
        for (LogEntity record : data.getRecords()) {
            LogDTO dto = LogConvert.INSTANCE.toLogDTO(record);
            // 安全修复：历史存量日志可能包含明文密码，响应前统一清除
            dto.setPassword(null);
            result.getRecords().add(dto);
        }
        return result;
    }
}
