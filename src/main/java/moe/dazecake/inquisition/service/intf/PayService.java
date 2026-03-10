package moe.dazecake.inquisition.service.intf;

import moe.dazecake.inquisition.model.entity.BillEntity;
import moe.dazecake.inquisition.utils.Result;

import java.util.List;
import java.util.Map;

public interface PayService {

    BillEntity createOrder(Double amount, String payType, String returnPath);

    String createBill(Long userId, String params, String type, Double amount, String payType, String returnPath);

    boolean billCallBackSolver(BillEntity bill);

    String payResultCallBack(String requestBody);

    Result<String> getAccountRenewalUrl(String token, String payType, Integer mo);

    Result<List<BillEntity>> getUserBills(String token);

    Result<BillEntity> getBillDetail(String token, Long billId);

    Result<Map<String, Object>> queryOrderStatus(String token, String orderNo);

    Result<String> closeOrder(String token, String orderNo);

    Result<String> applyRefund(String token, Long billId, String refundReason);

    Result<Map<String, Object>> approveRefund(String token, Long billId, Double refundAmount, boolean approve);

    Result<String> applyCheckBill(String token, String tranDate);

}
