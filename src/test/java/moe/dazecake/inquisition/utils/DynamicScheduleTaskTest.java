package moe.dazecake.inquisition.utils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import moe.dazecake.inquisition.mapper.LogMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志定时清理的边界用例。
 *
 * <p>清理是不可逆的物理删除，重点覆盖两类风险：保留天数配置错误导致误删整表，
 * 以及单轮清理失控长时间占用调度线程。
 */
public class DynamicScheduleTaskTest {

    private DynamicScheduleTask task;

    private LogMapper logMapper;

    @Before
    public void setUp() {
        task = new DynamicScheduleTask();
        logMapper = mock(LogMapper.class);
        task.logMapper = logMapper;
    }

    /**
     * 保留天数为 0 时 cutoff 等于当前时刻，等同于清空整表，必须拒绝执行。
     */
    @Test
    public void cleanExpiredLogsShouldSkipWhenRetentionDaysIsZero() {
        assertEquals(0, task.cleanExpiredLogs(0, 1000, 20));
        verify(logMapper, never()).delete(any(Wrapper.class));
    }

    /**
     * 保留天数为负数时同样拒绝执行。
     */
    @Test
    public void cleanExpiredLogsShouldSkipWhenRetentionDaysIsNegative() {
        assertEquals(0, task.cleanExpiredLogs(-1, 1000, 20));
        verify(logMapper, never()).delete(any(Wrapper.class));
    }

    /**
     * 最后一批未填满说明已无符合条件的数据，应提前结束，不再发起多余查询。
     */
    @Test
    public void cleanExpiredLogsShouldStopWhenBatchNotFull() {
        when(logMapper.delete(any(Wrapper.class))).thenReturn(1000, 400);

        assertEquals(1400, task.cleanExpiredLogs(30, 1000, 20));
        verify(logMapper, times(2)).delete(any(Wrapper.class));
    }

    /**
     * 每批都填满时，达到单轮批次上限必须停止，避免长时间独占调度线程。
     */
    @Test
    public void cleanExpiredLogsShouldStopAtMaxBatches() {
        when(logMapper.delete(any(Wrapper.class))).thenReturn(1000);

        assertEquals(5000, task.cleanExpiredLogs(30, 1000, 5));
        verify(logMapper, times(5)).delete(any(Wrapper.class));
    }

    /**
     * 没有符合条件的数据时只查询一次即结束。
     */
    @Test
    public void cleanExpiredLogsShouldStopWhenNothingToDelete() {
        when(logMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertEquals(0, task.cleanExpiredLogs(30, 1000, 20));
        verify(logMapper, times(1)).delete(any(Wrapper.class));
    }

    /**
     * 分批参数配置非法时回退默认值，既不抛异常也不退化为全表删除。
     */
    @Test
    public void cleanExpiredLogsShouldFallbackWhenBatchConfigInvalid() {
        when(logMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertEquals(0, task.cleanExpiredLogs(30, 0, 0));
        verify(logMapper, times(1)).delete(any(Wrapper.class));
    }
}
