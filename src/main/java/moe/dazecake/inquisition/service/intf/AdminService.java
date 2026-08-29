package moe.dazecake.inquisition.service.intf;

import moe.dazecake.inquisition.model.dto.admin.ChangeAdminPasswordDTO;
import moe.dazecake.inquisition.model.dto.admin.LoginAdminDTO;
import moe.dazecake.inquisition.model.entity.BillEntity;
import moe.dazecake.inquisition.model.vo.admin.AddProUserBalanceDTO;
import moe.dazecake.inquisition.model.vo.admin.AdminLoginVO;
import moe.dazecake.inquisition.model.vo.query.PageQueryVO;
import moe.dazecake.inquisition.utils.Result;

public interface AdminService {

    /**
     * 登录管理员账户
     *
     * @param loginAdminDTO 账号密码
     * @return: moe.dazecake.inquisition.utils.Result<java.lang.String> 返回类
     * @author DazeCake
     * @date 2023/1/26 10:23
     */
    Result<AdminLoginVO> loginAdmin(LoginAdminDTO loginAdminDTO);

    Result<String> updateAdminPassword(ChangeAdminPasswordDTO changeAdminPasswordDTO);

    /**
     * 增加代理用户余额
     *
     * @param addProUserBalanceDTO 代理id和增加余额
     * @return: moe.dazecake.inquisition.utils.Result<java.lang.String> 返回消息
     * @author DazeCake
     * @date 2023/1/26 10:57
     */
    Result<String> addBalanceForProUser(AddProUserBalanceDTO addProUserBalanceDTO);

    /**
     * 分页查询所有订单
     *
     * @param current 当前页
     * @param size    每页大小
     * @param userId  用户ID（可选）
     * @param state   订单状态（可选）
     * @param orderNo 订单号（可选）
     * @param payType 支付类型（可选）
     * @return: moe.dazecake.inquisition.utils.Result 分页订单列表
     */
    Result<PageQueryVO<BillEntity>> getAllBill(Long current, Long size, Long userId, Integer state, String orderNo,
            String payType);

}
