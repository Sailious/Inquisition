package moe.dazecake.inquisition.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import moe.dazecake.inquisition.mapper.AdminMapper;
import moe.dazecake.inquisition.mapper.ProUserMapper;
import moe.dazecake.inquisition.model.dto.admin.ChangeAdminPasswordDTO;
import moe.dazecake.inquisition.model.dto.admin.LoginAdminDTO;
import moe.dazecake.inquisition.model.entity.AdminEntity;
import moe.dazecake.inquisition.model.vo.admin.AddProUserBalanceDTO;
import moe.dazecake.inquisition.model.vo.admin.AdminLoginVO;
import moe.dazecake.inquisition.service.intf.AdminService;
import moe.dazecake.inquisition.utils.Encoder;
import moe.dazecake.inquisition.utils.JWTUtils;
import moe.dazecake.inquisition.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    /** 旧版密码盐值，仅用于兼容历史 MD5 哈希的登录校验 */
    private static final String LEGACY_SALT = "arklightscloud";

    @Resource
    AdminMapper adminMapper;

    @Resource
    ProUserMapper proUserMapper;

    @Override
    public Result<AdminLoginVO> loginAdmin(LoginAdminDTO loginAdminDTO) {
        if (loginAdminDTO.getUsername() == null || loginAdminDTO.getPassword() == null) {
            return Result.paramError("用户名或密码为空");
        }

        // 先按用户名查询，再校验密码（避免在SQL条件中比对哈希）
        var admin = adminMapper.selectOne(
                Wrappers.<AdminEntity>lambdaQuery()
                        .eq(AdminEntity::getUsername, loginAdminDTO.getUsername()));

        if (admin != null && verifyPassword(loginAdminDTO.getPassword(), admin.getPassword())) {
            // 旧版 MD5 哈希登录成功时，自动升级为 BCrypt。
            // 若密码超过 BCrypt 最大 72 字节（CVE-2025-22228 缓解），跳过升级保留 MD5，
            // 避免 Encoder.BCrypt() 抛出异常导致登录流程中断。
            if (!isBcrypt(admin.getPassword()) && Encoder.isBcryptPasswordValid(loginAdminDTO.getPassword())) {
                admin.setPassword(Encoder.BCrypt(loginAdminDTO.getPassword()));
                adminMapper.updateById(admin);
                log.info("管理员 {} 的密码已自动升级为 BCrypt", admin.getUsername());
            }
            return Result.success(new AdminLoginVO(JWTUtils.generateTokenForAdmin(admin)), "登录成功");
        } else {
            return Result.unauthorized("用户名或密码错误");
        }
    }

    /**
     * 密码校验：优先 BCrypt，失败则回退旧版 MD5(密码+盐)。
     * MD5 不可逆，无法批量迁移，故采用「登录时校验并升级」的方式平滑过渡。
     */
    private boolean verifyPassword(String rawPassword, String stored) {
        if (stored == null) {
            return false;
        }
        if (Encoder.BCryptMatches(rawPassword, stored)) {
            return true;
        }
        return Encoder.MD5(rawPassword + LEGACY_SALT).equalsIgnoreCase(stored);
    }

    /** 判断是否为 BCrypt 哈希（以 $2a$ / $2b$ / $2y$ 开头） */
    private boolean isBcrypt(String hash) {
        return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
    }

    @Override
    public Result<String> updateAdminPassword(ChangeAdminPasswordDTO changeAdminPasswordDTO) {
        if (changeAdminPasswordDTO.getUsername() == null || changeAdminPasswordDTO.getOldPassword() == null
                || changeAdminPasswordDTO.getNewPassword() == null) {
            return Result.paramError("用户名或密码为空");
        }

        // CVE-2025-22228 缓解：BCrypt 仅处理前 72 字节，超长密码会被截断比较
        if (!Encoder.isBcryptPasswordValid(changeAdminPasswordDTO.getNewPassword())) {
            return Result.paramError("新密码不能超过 " + Encoder.BCRYPT_MAX_PASSWORD_BYTES + " 字节");
        }

        var admin = adminMapper.selectOne(
                Wrappers.<AdminEntity>lambdaQuery()
                        .eq(AdminEntity::getUsername, changeAdminPasswordDTO.getUsername()));

        if (admin != null && verifyPassword(changeAdminPasswordDTO.getOldPassword(), admin.getPassword())) {
            admin.setPassword(Encoder.BCrypt(changeAdminPasswordDTO.getNewPassword()));
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
}
