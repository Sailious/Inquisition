package moe.dazecake.inquisition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import moe.dazecake.inquisition.annotation.Login;
import moe.dazecake.inquisition.model.dto.admin.ChangeAdminPasswordDTO;
import moe.dazecake.inquisition.model.dto.admin.LoginAdminDTO;
import moe.dazecake.inquisition.model.dto.admin.SetServerStatueDTO;
import moe.dazecake.inquisition.model.vo.admin.AddProUserBalanceDTO;
import moe.dazecake.inquisition.model.vo.admin.AdminLoginVO;
import moe.dazecake.inquisition.service.impl.AdminServiceImpl;
import moe.dazecake.inquisition.utils.DynamicInfo;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Tag(name = "管理员接口", description = "管理员登录、密码修改、服务器状态管理等功能")
@ResponseBody
@RestController
public class AdminController {

    @Resource
    AdminServiceImpl adminService;

    @Resource
    DynamicInfo dynamicInfo;

    @Operation(summary = "管理员登陆", description = "使用管理员账号和密码登陆系统，获取JWT Token")
    @PostMapping("/adminLogin")
    public Result<AdminLoginVO> adminLogin(
            @Parameter(description = "管理员登录信息", required = true)
            @RequestBody LoginAdminDTO loginAdminDTO) {
        return adminService.loginAdmin(loginAdminDTO);
    }

    @Login
    @Operation(summary = "修改管理员密码", description = "修改当前管理员的密码")
    @PostMapping("/changeAdminPassword")
    public Result<String> changeAdminPassword(
            @Parameter(description = "修改密码信息", required = true)
            @RequestBody ChangeAdminPasswordDTO changeAdminPasswordDTO) {
        return adminService.updateAdminPassword(changeAdminPasswordDTO);
    }

    @Login
    @Operation(summary = "为pro_user增加余额", description = "为专业用户账户增加余额")
    @PostMapping("/addBalanceForProUser")
    public Result<String> addBalanceForProUser(
            @Parameter(description = "增加余额信息", required = true)
            @RequestBody AddProUserBalanceDTO addProUserBalanceDTO) {
        return adminService.addBalanceForProUser(addProUserBalanceDTO);
    }

    @Login
    @Operation(summary = "获取服务器启用状态", description = "获取当前服务器的启用状态")
    @GetMapping("/getServerStatus")
    public Result<Boolean> getServerStatus() {
        return Result.success(dynamicInfo.getActive(), "获取成功");
    }

    @Login
    @Operation(summary = "设置服务器启用状态", description = "开启或关闭服务器")
    @PostMapping("/setServerStatus")
    public Result<String> setServerStatus(
            @Parameter(description = "服务器状态", required = true)
            @RequestBody SetServerStatueDTO statueDTO) {
        dynamicInfo.setActive(statueDTO.getActive());
        return Result.success("设置成功");
    }

}
