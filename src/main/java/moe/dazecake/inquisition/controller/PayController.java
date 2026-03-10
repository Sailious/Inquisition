package moe.dazecake.inquisition.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.annotation.UserLogin;
import moe.dazecake.inquisition.model.dto.pay.AccountRenewalDTO;
import moe.dazecake.inquisition.model.entity.BillEntity;
import moe.dazecake.inquisition.service.impl.PayServiceImpl;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "支付接口")
@ResponseBody
@RestController
public class PayController {

    @Resource
    PayServiceImpl payService;

    @Operation(summary = "支付结果回调")
    @PostMapping("/payResultCallBack")
    public String payResultCallBack(@RequestBody String requestBody) {
        log.info("【支付回调】收到回调: {}", requestBody);
        return payService.payResultCallBack(requestBody);
    }

    @UserLogin
    @Operation(summary = "获取账号续费url")
    @PostMapping("/getAccountRenewalUrl")
    public Result<String> getAccountRenewalUrl(@RequestHeader("Authorization") String token,
            @RequestBody AccountRenewalDTO accountRenewalDTO) {
        return payService.getAccountRenewalUrl(token, accountRenewalDTO.getPayType(), accountRenewalDTO.getMo());
    }

    @UserLogin
    @Operation(summary = "获取用户订单列表")
    @GetMapping("/getUserBills")
    public Result<List<BillEntity>> getUserBills(@RequestHeader("Authorization") String token) {
        return payService.getUserBills(token);
    }

    @UserLogin
    @Operation(summary = "获取订单详情")
    @GetMapping("/getBillDetail")
    public Result<BillEntity> getBillDetail(@RequestHeader("Authorization") String token,
            @RequestParam Long billId) {
        return payService.getBillDetail(token, billId);
    }

    @UserLogin
    @Operation(summary = "查询订单状态")
    @GetMapping("/queryOrderStatus")
    public Result<Map<String, Object>> queryOrderStatus(@RequestHeader("Authorization") String token,
            @RequestParam String orderNo) {
        return payService.queryOrderStatus(token, orderNo);
    }

    @UserLogin
    @Operation(summary = "关闭订单")
    @PostMapping("/closeOrder")
    public Result<String> closeOrder(@RequestHeader("Authorization") String token,
            @RequestParam String orderNo) {
        return payService.closeOrder(token, orderNo);
    }

    @UserLogin
    @Operation(summary = "申请退款")
    @PostMapping("/applyRefund")
    public Result<String> applyRefund(@RequestHeader("Authorization") String token,
            @RequestParam Long billId,
            @RequestParam String refundReason) {
        return payService.applyRefund(token, billId, refundReason);
    }

    @UserLogin
    @Operation(summary = "审核退款（管理员）")
    @PostMapping("/approveRefund")
    public Result<Map<String, Object>> approveRefund(@RequestHeader("Authorization") String token,
            @RequestParam Long billId,
            @RequestParam Double refundAmount,
            @RequestParam Boolean approve) {
        return payService.approveRefund(token, billId, refundAmount, approve);
    }

    @UserLogin
    @Operation(summary = "申请对账单")
    @GetMapping("/applyCheckBill")
    public Result<String> applyCheckBill(@RequestHeader("Authorization") String token,
            @RequestParam String tranDate) {
        return payService.applyCheckBill(token, tranDate);
    }

}
