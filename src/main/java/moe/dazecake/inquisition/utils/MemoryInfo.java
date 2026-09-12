package moe.dazecake.inquisition.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import moe.dazecake.inquisition.model.local.UserSan;
import moe.dazecake.inquisition.model.local.WorkUser;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存态数据结构，同时作为 {@code config/data.json} 的序列化载体。
 *
 * <p>并发约定：这些集合同时被 HTTP 请求线程与定时任务线程读写
 * （典型如每 5 秒的设备巡检、每 6 秒的理智刷新、用户查询接口的遍历），
 * 因此统一使用并发容器。
 *
 * <p>重要：字段必须声明为<b>具体容器类型</b>而非 {@code List} / {@code Map} 接口。
 * 本类经 Gson 反序列化（{@code RunScript} 启动时加载 {@code data.json}），
 * Gson 依据字段声明类型实例化——若声明为接口，会退化成普通 ArrayList / HashMap，
 * 线程安全被静默破坏（历史上 workUserList 即存在此隐患）。
 * CopyOnWriteArrayList 与 ConcurrentHashMap 均有公开无参构造，Gson 可正常实例化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryInfo {

    //======================
    //全局启动
    //======================
    public Boolean active = true;

    //======================
    //队列 仅存储用户ID
    //======================
    // 读远多于写，且元素数量有限，适合写时复制
    public CopyOnWriteArrayList<Long> waitUserList = new CopyOnWriteArrayList<>();
    public CopyOnWriteArrayList<Long> workUserList = new CopyOnWriteArrayList<>();
    public CopyOnWriteArrayList<String> haltList = new CopyOnWriteArrayList<>();

    //======================
    //队列关系映射表 用于映射队列关系信息
    //======================
    public ConcurrentHashMap<Long, UserSan> userSanInfoMap = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Long, WorkUser> workUserInfoMap = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Long, LocalDateTime> freezeUserInfoMap = new ConcurrentHashMap<>();


    //======================
    //额外映射表 用于映射其他信息
    //======================

    //设备状态映射表
    public ConcurrentHashMap<String, Integer> deviceStatusMap = new ConcurrentHashMap<>();

    //设备摇篮计数器
    public ConcurrentHashMap<String, Integer> deviceCounterMap = new ConcurrentHashMap<>();

    //公告信息
    public ConcurrentHashMap<String, String> announcement = new ConcurrentHashMap<>();

}
