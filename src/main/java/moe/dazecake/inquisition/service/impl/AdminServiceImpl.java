package moe.dazecake.inquisition.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import moe.dazecake.inquisition.mapper.AdminMapper;
import moe.dazecake.inquisition.mapper.BillMapper;
import moe.dazecake.inquisition.mapper.ProUserMapper;
import moe.dazecake.inquisition.model.dto.admin.ChangeAdminPasswordDTO;
import moe.dazecake.inquisition.model.dto.admin.LoginAdminDTO;
import moe.dazecake.inquisition.model.entity.AdminEntity;
import moe.dazecake.inquisition.model.entity.BillEntity;
import moe.dazecake.inquisition.model.vo.admin.AddProUserBalanceDTO;
import moe.dazecake.inquisition.model.vo.admin.AdminLoginVO;
import moe.dazecake.inquisition.model.vo.query.PageQueryVO;
import moe.dazecake.inquisition.service.intf.AdminService;
import moe.dazecake.inquisition.utils.Encoder;
import moe.dazecake.inquisition.utils.JWTUtils;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class AdminServiceImpl implements AdminService {

    private static final String salt = "arklightscloud";

    @Resource
    AdminMapper adminMapper;

    @Resource
    ProUserMapper proUserMapper;

    @Resource
    BillMapper billMapper;

    @Override
    public Result<AdminLoginVO> loginAdmin(LoginAdminDTO loginAdminDTO) {
        if (loginAdminDTO.getUsername() == null || loginAdminDTO.getPassword() == null) {
            return Result.paramError("用户名或密码为空");
        }

        var admin = adminMapper.selectOne(
                Wrappers.<AdminEntity>lambdaQuery()
                        .eq(AdminEntity::getUsername, loginAdminDTO.getUsername())
                        .eq(AdminEntity::getPassword, Encoder.MD5(loginAdminDTO.getPassword() + salt)));

        if (admin != null) {
            return Result.success(new AdminLoginVO(JWTUtils.generateTokenForAdmin(admin)), "登录成功");
        } else {
            return Result.unauthorized("用户名或密码错误");
        }
    }

    @Override
    public Result<String> updateAdminPassword(ChangeAdminPasswordDTO changeAdminPasswordDTO) {
        if (changeAdminPasswordDTO.getUsername() == null || changeAdminPasswordDTO.getOldPassword() == null
                || changeAdminPasswordDTO.getNewPassword() == null) {
            return Result.paramError("用户名或密码为空");
        }

        var admin = adminMapper.selectOne(
                Wrappers.<AdminEntity>lambdaQuery()
                        .eq(AdminEntity::getUsername, changeAdminPasswordDTO.getUsername())
                        .eq(AdminEntity::getPassword, Encoder.MD5(changeAdminPasswordDTO.getOldPassword() + salt)));

        if (admin != null) {
            admin.setPassword(Encoder.MD5(changeAdminPasswordDTO.getNewPassword() + salt));
            adminMapper.updateById(admin);
            return Result.success("修改成功");
        } else {
            return Result.unauthorized("用户名或密码错误");
        }
    }

    @Override
    public Result<String> addBalanceForProUser(AddProUserBalanceDTO addProUserBalanceDTO) {
        var proUser = proUserMapper.selectById(addProUserBalanceDTO.getId());
        if (proUser != null) {
            proUser.setBalance(proUser.getBalance() + addProUserBalanceDTO.getBalance());
            proUserMapper.updateById(proUser);
            return Result.success("添加成功");
        } else {
            return Result.notFound("用户不存在");
        }
    }

    @Override
    public Result<PageQueryVO<BillEntity>> getAllBill(Long current, Long size, Long userId, Integer state,
            String orderNo, String payType) {
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 10L;
        }

        var queryWrapper = Wrappers.<BillEntity>lambdaQuery()
                .orderByDesc(BillEntity::getId);

        if (userId != null) {
            queryWrapper.eq(BillEntity::getUserId, userId);
        }
        if (state != null) {
            queryWrapper.eq(BillEntity::getState, state);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            queryWrapper.like(BillEntity::getOrderNo, orderNo);
        }
        if (payType != null && !payType.isEmpty()) {
            queryWrapper.eq(BillEntity::getPayType, payType);
        }

        var page = billMapper.selectPage(new Page<>(current, size), queryWrapper);

        var pageQueryVO = new PageQueryVO<BillEntity>();
        pageQueryVO.setCurrent(page.getCurrent());
        pageQueryVO.setPage(page.getPages());
        pageQueryVO.setTotal(page.getTotal());
        pageQueryVO.setRecords(page.getRecords());

        return Result.success(pageQueryVO, "查询成功");
    }
}
