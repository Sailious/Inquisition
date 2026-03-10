package moe.dazecake.inquisition.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lakala.moss.api.ApiReq;
import com.lakala.moss.api.ApiRes;
import com.lakala.moss.api.request.OrderPayReq;
import com.lakala.moss.api.request.OrderNoticeReq;
import com.lakala.moss.api.request.OrderQryListReq;
import com.lakala.moss.api.request.OrderClsReq;
import com.lakala.moss.api.request.OrderRefundReq;
import com.lakala.moss.api.request.OrderChkApplyReq;
import com.lakala.moss.api.params.LocationInfo;
import com.lakala.moss.api.response.OrderPayRes;
import com.lakala.moss.api.response.OrderNoticeRes;
import com.lakala.moss.api.response.OrderClsRes;
import com.lakala.moss.api.response.OrderRefundRes;
import com.lakala.moss.api.response.OrderChkApplyRes;
import com.lakala.moss.api.response.OrderQryListRes;
import com.lakala.moss.service.IMossApiService;
import lombok.extern.slf4j.Slf4j;
import moe.dazecake.inquisition.constant.enums.TaskType;
import moe.dazecake.inquisition.mapper.AccountMapper;
import moe.dazecake.inquisition.mapper.BillMapper;
import moe.dazecake.inquisition.mapper.ProUserMapper;
import moe.dazecake.inquisition.model.entity.AccountEntity;
import moe.dazecake.inquisition.model.entity.BillEntity;
import moe.dazecake.inquisition.service.intf.PayService;
import moe.dazecake.inquisition.utils.JWTUtils;
import moe.dazecake.inquisition.utils.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PayServiceImpl implements PayService {

    @Value("${inquisition.pay.enablePay:false}")
    boolean enablePay;

    @Value("${inquisition.backUrl:}")
    String backUrl;

    @Value("${inquisition.frontUrl:}")
    String frontUrl;

    @Value("${inquisition.price.daily:1.0}")
    private Double dailyPrice;

    @Resource
    AccountMapper accountMapper;

    @Resource
    ProUserMapper proUserMapper;

    @Resource
    BillMapper billMapper;

    @Resource
    MessageServiceImpl messageService;

    @Resource
    AccountServiceImpl accountService;

    @Resource
    IMossApiService mossApiService;

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    @Override
    public BillEntity createOrder(Double amount, String payType, String returnPath) {
        if (!enablePay || backUrl.equals("")) {
            return null;
        }

        String orderNo = generateOrderNo();
        String totalAmount = String.valueOf((long) (amount * 100));

        OrderPayReq req = new OrderPayReq();
        req.setOrder_no(orderNo);
        req.setTotal_amount(totalAmount);
        req.setMer_no("M00000036");
        req.setRemark("Inquisition支付");
        req.setNotify_url(backUrl + "/payResultCallBack");
        req.setPay_scene("0");
        req.setAccount_type("ALIPAY,WECHAT,UQRCODEPAY");
        req.setSubject("Inquisition账户充值");
        req.setOrder_eff_time("30");
        if (returnPath != null) {
            req.setCallback_url(frontUrl + returnPath);
        }

        try {
            ApiRes<OrderPayRes> res = mossApiService.OrderPay(req);

            if (res.getHead() != null && "000000".equals(res.getHead().getCode())) {
                OrderPayRes payRes = res.getResponse();
                String payUrl = payRes.getCounter_url();

                BillEntity bill = new BillEntity();
                bill.setOrderNo(orderNo)
                        .setPlatformOrderNo(payRes.getPay_serial())
                        .setPayType(payType)
                        .setPayUrl(payUrl)
                        .setAmount(amount)
                        .setState(0)
                        .setUpdateTime(LocalDateTime.now());
                billMapper.insert(bill);
                return billMapper.selectOne(Wrappers.<BillEntity>lambdaQuery()
                        .eq(BillEntity::getPlatformOrderNo, payRes.getPay_serial()));
            } else {
                log.warn("【支付】 订单创建失败: " + (res.getHead() != null ? res.getHead().getDesc() : "未知错误"));
                return null;
            }
        } catch (Exception e) {
            log.error("【支付】 订单创建异常", e);
            return null;
        }
    }

    private String generateOrderNo() {
        return "INQ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                String.format("%04d", (int) (Math.random() * 10000));
    }

    @Override
    public String createBill(Long userId, String params, String type, Double amount, String payType,
            String returnPath) {
        var bill = createOrder(amount, payType, returnPath);
        if (bill == null) {
            return null;
        }
        bill.setUserId(userId)
                .setParam(params)
                .setType(type);
        billMapper.updateById(bill);
        return bill.getPayUrl();
    }

    @Override
    public boolean billCallBackSolver(BillEntity bill) {

        switch (TaskType.getByStr(bill.getType())) {
            case DAILY:
                var user = accountMapper.selectById(bill.getUserId());

                accountService.addAccountExpireTime(user.getId(), 24 * Integer.parseInt(bill.getParam()));

                if (user.getAgent() != null) {
                    if (user.getAgent() != 0) {
                        calculateCommission(user.getAgent(), dailyPrice * Integer.parseInt(bill.getParam()));
                    }
                } else {
                    user.setAgent(0L);
                }

                accountMapper.updateById(user);
                return true;
            case UNKNOWN:
                if (bill.getType().equals("register")) {
                    var newUser = new AccountEntity();
                    newUser.setName(bill.getParam().split("\\|")[0]);
                    newUser.setAccount(bill.getParam().split("\\|")[1]);
                    newUser.setPassword(bill.getParam().split("\\|")[2]);
                    newUser.setServer(Long.valueOf(bill.getParam().split("\\|")[3]));
                    newUser.setAgent(Long.valueOf(bill.getParam().split("\\|")[4]));
                    newUser.setExpireTime(LocalDateTime.now().plusDays(3));
                    accountMapper.insert(newUser);
                    var userId = accountMapper.selectOne(
                            Wrappers.<AccountEntity>lambdaQuery()
                                    .eq(AccountEntity::getAccount, newUser.getAccount()))
                            .getId();
                    accountService.forceFightAccount(userId, true);
                    if (newUser.getAgent() != 0) {
                        calculateCommission(newUser.getAgent(), 1.0);
                    }
                    return true;
                }
                break;
            default:
                return accountService.initiateTaskConversion(TaskType.getByStr(bill.getType()), bill.getUserId(),
                        bill.getParam());
        }

        return false;
    }

    @Override
    public String payResultCallBack(String requestBody) {
        try {
            log.info("【支付回调】 收到回调: {}", requestBody);

            Type type = new TypeToken<ApiReq<OrderNoticeReq>>() {
            }.getType();
            ApiReq<OrderNoticeReq> req = gson.fromJson(requestBody, type);

            ApiRes<OrderNoticeRes> res = mossApiService.OrderNotice(req);

            if (res.getHead() != null && "000000".equals(res.getHead().getCode())) {
                String orderNo = req.getRequest() != null ? req.getRequest().getOrder_no() : null;
                String tradeMainType = req.getRequest() != null ? req.getRequest().getTrade_main_type() : null;
                String tradeState = req.getRequest() != null ? req.getRequest().getTrade_state() : null;

                if (orderNo == null) {
                    log.warn("【支付回调】 无法获取订单号");
                    return "{\"head\":{\"code\":\"999999\",\"desc\":\"param error\"}}";
                }

                var bill = billMapper.selectOne(Wrappers.<BillEntity>lambdaQuery()
                        .eq(BillEntity::getOrderNo, orderNo));

                if (bill == null) {
                    log.warn("【支付回调】 账单获取出错, orderNo: {}", orderNo);
                    return "{\"head\":{\"code\":\"500\",\"desc\":\"order not found\"}}";
                }

                if ("PAY".equals(tradeMainType) && "SUCCESS".equals(tradeState)) {
                    if (bill.getState() == 1) {
                        log.info("【支付回调】 订单已处理过, orderNo: {}", orderNo);
                        return "{\"head\":{\"code\":\"000000\",\"desc\":\"success\"}}";
                    }
                    bill.setState(1)
                            .setUpdateTime(LocalDateTime.now());
                    billMapper.updateById(bill);

                    if (billCallBackSolver(bill)) {
                        log.info("【支付回调】 支付成功, orderNo: {}", orderNo);
                    } else {
                        log.info("【支付回调】 支付成功, 解决失败, orderNo: {}", orderNo);
                        messageService.pushAdmin("支付成功, 但是解决失败", "订单：" + bill.getId() + " 支付成功, 但是解决失败");
                    }
                    return "{\"head\":{\"code\":\"000000\",\"desc\":\"success\"}}";
                } else if ("REFUND".equals(tradeMainType) && "SUCCESS".equals(tradeState)) {
                    if (bill.getState() == 3) {
                        log.info("【支付回调】 退款已处理过, orderNo: {}", orderNo);
                        return "{\"head\":{\"code\":\"000000\",\"desc\":\"success\"}}";
                    }
                    bill.setState(3)
                            .setUpdateTime(LocalDateTime.now());
                    billMapper.updateById(bill);
                    log.info("【支付回调】 退款成功, orderNo: {}", orderNo);
                    return "{\"head\":{\"code\":\"000000\",\"desc\":\"success\"}}";
                } else if ("REFUND".equals(tradeMainType) && "DEAL".equals(tradeState)) {
                    log.info("【支付回调】 退款处理中, orderNo: {}", orderNo);
                    return "{\"head\":{\"code\":\"000000\",\"desc\":\"success\"}}";
                } else {
                    log.warn("【支付回调】 交易状态错误: tradeMainType={}, tradeState={}, orderNo={}", tradeMainType, tradeState,
                            orderNo);
                    return "{\"head\":{\"code\":\"999999\",\"desc\":\"trade state error\"}}";
                }
            } else {
                log.warn("【支付回调】 验签失败或处理失败");
                return "{\"head\":{\"code\":\"999999\",\"desc\":\"verify error\"}}";
            }

        } catch (Exception e) {
            log.error("【支付回调】 处理异常", e);
            return "error";
        }
    }

    @Override
    public Result<String> getAccountRenewalUrl(String token, String payType, Integer mo) {
        var id = JWTUtils.getId(token);
        if (mo < 1) {
            return Result.failed("不允许购买小于1个月的套餐");
        }

        String url = createBill(id, String.valueOf(30 * mo), TaskType.DAILY.getType(), mo * 30 * dailyPrice, payType,
                "/user/home/");
        if (url != null && !url.isEmpty()) {
            return Result.success(url, "获取成功");
        } else {
            return Result.failed("获取账单失败，稍后重试");
        }
    }

    @Override
    public Result<List<BillEntity>> getUserBills(String token) {
        var userId = JWTUtils.getId(token);
        var bills = billMapper.selectList(Wrappers.<BillEntity>lambdaQuery()
                .eq(BillEntity::getUserId, userId)
                .orderByDesc(BillEntity::getUpdateTime));
        return Result.success(bills, "获取成功");
    }

    @Override
    public Result<BillEntity> getBillDetail(String token, Long billId) {
        var userId = JWTUtils.getId(token);
        var bill = billMapper.selectById(billId);
        if (bill == null) {
            return Result.failed("订单不存在");
        }
        if (!bill.getUserId().equals(userId)) {
            return Result.failed("无权限查看此订单");
        }
        return Result.success(bill, "获取成功");
    }

    @Override
    public Result<Map<String, Object>> queryOrderStatus(String token, String orderNo) {
        var userId = JWTUtils.getId(token);

        var localBill = billMapper.selectOne(Wrappers.<BillEntity>lambdaQuery()
                .eq(BillEntity::getOrderNo, orderNo)
                .eq(BillEntity::getUserId, userId));

        if (localBill == null) {
            return Result.failed("订单不存在");
        }

        try {
            OrderQryListReq req = new OrderQryListReq();
            req.setOrder_no(orderNo);

            ApiRes<OrderQryListRes> res = mossApiService.OrderQryList(req);

            if (res.getHead() != null && "000000".equals(res.getHead().getCode())) {
                OrderQryListRes qryRes = res.getResponse();
                Map<String, Object> result = new HashMap<>();
                result.put("orderNo", qryRes.getOrder_no());
                result.put("tradeMainType", qryRes.getTrade_main_type());
                result.put("orderStatus", qryRes.getOrder_status());
                result.put("totalAmount", qryRes.getTotal_amount());
                result.put("subject", qryRes.getSubject());
                result.put("orderCreateTime", qryRes.getOrder_create_time());
                result.put("merNo", qryRes.getMer_no());
                result.put("transType", qryRes.getTrans_type());

                if (qryRes.getPay_info_list() != null && !qryRes.getPay_info_list().isEmpty()) {
                    var payInfo = qryRes.getPay_info_list().get(0);
                    Map<String, Object> payInfoMap = new HashMap<>();
                    payInfoMap.put("paySerial", payInfo.getPay_serial());
                    payInfoMap.put("tradeState", payInfo.getTrade_state());
                    payInfoMap.put("tradeTime", payInfo.getTrade_time());
                    payInfoMap.put("accountType", payInfo.getAccount_type());
                    result.put("payInfo", payInfoMap);
                }

                return Result.success(result, "查询成功");
            } else {
                return Result.failed("查询失败: " + (res.getHead() != null ? res.getHead().getDesc() : "未知错误"));
            }
        } catch (Exception e) {
            log.error("【订单查询】查询异常", e);
            return Result.failed("查询异常: " + e.getMessage());
        }
    }

    @Override
    public Result<String> closeOrder(String token, String orderNo) {
        var userId = JWTUtils.getId(token);

        var localBill = billMapper.selectOne(Wrappers.<BillEntity>lambdaQuery()
                .eq(BillEntity::getOrderNo, orderNo)
                .eq(BillEntity::getUserId, userId));

        if (localBill == null) {
            return Result.failed("订单不存在");
        }

        if (localBill.getState() == 1) {
            return Result.failed("订单已支付，无法关闭");
        }

        try {
            OrderClsReq req = new OrderClsReq();
            req.setOrigin_order_no(orderNo);

            LocationInfo locationInfo = new LocationInfo();
            locationInfo.setRequest_ip("0.0.0.0");
            req.setLocation_info(locationInfo);

            ApiRes<OrderClsRes> res = mossApiService.OrderCls(req);

            if (res.getHead() != null && "000000".equals(res.getHead().getCode())) {
                localBill.setState(2)
                        .setUpdateTime(LocalDateTime.now());
                billMapper.updateById(localBill);
                log.info("【订单关单】关单成功, orderNo: {}", orderNo);
                return Result.success("关单成功");
            } else {
                log.warn("【订单关单】关单失败: {}", res.getHead() != null ? res.getHead().getDesc() : "未知错误");
                return Result.failed("关单失败: " + (res.getHead() != null ? res.getHead().getDesc() : "未知错误"));
            }
        } catch (Exception e) {
            log.error("【订单关单】关单异常", e);
            return Result.failed("关单异常: " + e.getMessage());
        }
    }

    @Override
    public Result<String> applyRefund(String token, Long billId, String refundReason) {
        var userId = JWTUtils.getId(token);

        var localBill = billMapper.selectById(billId);
        if (localBill == null) {
            return Result.failed("订单不存在");
        }

        if (!localBill.getUserId().equals(userId)) {
            return Result.failed("无权限操作此订单");
        }

        if (localBill.getState() != 1) {
            return Result.failed("订单未支付，无法申请退款");
        }

        if (localBill.getState() == 4) {
            return Result.failed("退款申请已提交，请等待审核");
        }

        if (localBill.getState() == 3) {
            return Result.failed("订单已退款");
        }

        localBill.setState(4)
                .setRefundReason(refundReason)
                .setUpdateTime(LocalDateTime.now());
        billMapper.updateById(localBill);

        log.info("【退款申请】用户申请退款, billId: {}, orderNo: {}", billId, localBill.getOrderNo());
        return Result.success("退款申请已提交，请等待管理员审核");
    }

    @Override
    public Result<Map<String, Object>> approveRefund(String token, Long billId, Double refundAmount, boolean approve) {
        var localBill = billMapper.selectById(billId);
        if (localBill == null) {
            return Result.failed("订单不存在");
        }

        if (localBill.getState() != 4) {
            return Result.failed("订单不在退款申请状态");
        }

        if (!approve) {
            localBill.setState(1)
                    .setUpdateTime(LocalDateTime.now());
            billMapper.updateById(localBill);
            log.info("【退款审核】管理员拒绝退款, billId: {}", billId);
            return Result.success("已拒绝退款申请");
        }

        double maxRefund = localBill.getActualPayAmount() != null ? localBill.getActualPayAmount()
                : localBill.getAmount();
        if (refundAmount > maxRefund) {
            return Result.failed("退款金额超出实际支付金额");
        }

        try {
            String refundOrderNo = "REF" + System.currentTimeMillis();

            OrderRefundReq req = new OrderRefundReq();
            req.setOrder_no(refundOrderNo);
            req.setOrigin_order_no(localBill.getOrderNo());
            req.setOrigin_pay_serial(localBill.getPlatformOrderNo());
            req.setRefund_amount(String.valueOf((long) (refundAmount * 100)));
            req.setRefund_reason(localBill.getRefundReason());

            LocationInfo locationInfo = new LocationInfo();
            locationInfo.setRequest_ip("0.0.0.0");
            req.setLocation_info(locationInfo);

            ApiRes<OrderRefundRes> res = mossApiService.OrderRefund(req);

            if (res.getHead() != null && "000000".equals(res.getHead().getCode())) {
                OrderRefundRes refundRes = res.getResponse();

                if ("SUCCESS".equals(refundRes.getTrade_state())) {
                    localBill.setState(3)
                            .setUpdateTime(LocalDateTime.now());
                    billMapper.updateById(localBill);

                    accountService.addAccountExpireTime(localBill.getUserId(), -24 * (int) (refundAmount * 30));
                    accountService.clearExpiredTime(localBill.getUserId());

                    log.info("【退款审核】退款成功, orderNo: {}, refundAmount: {}", localBill.getOrderNo(), refundAmount);
                    Map<String, Object> result = new HashMap<>();
                    result.put("refundOrderNo", refundOrderNo);
                    result.put("refundAmount", refundAmount);
                    result.put("tradeState", refundRes.getTrade_state());
                    return Result.success(result, "退款成功");
                } else {
                    log.warn("【退款审核】退款处理中, orderNo: {}, tradeState: {}", localBill.getOrderNo(),
                            refundRes.getTrade_state());
                    Map<String, Object> result = new HashMap<>();
                    result.put("refundOrderNo", refundOrderNo);
                    result.put("refundAmount", refundAmount);
                    result.put("tradeState", refundRes.getTrade_state());
                    return Result.success(result, "退款处理中");
                }
            } else {
                log.warn("【退款审核】退款失败: {}", res.getHead() != null ? res.getHead().getDesc() : "未知错误");
                return Result.failed("退款失败: " + (res.getHead() != null ? res.getHead().getDesc() : "未知错误"));
            }
        } catch (Exception e) {
            log.error("【退款审核】退款异常", e);
            return Result.failed("退款异常: " + e.getMessage());
        }
    }

    @Override
    public Result<String> applyCheckBill(String token, String tranDate) {
        try {
            String applyOrderNo = "CHK" + System.currentTimeMillis();

            OrderChkApplyReq req = new OrderChkApplyReq();
            req.setTran_date(tranDate);
            req.setApply_order_no(applyOrderNo);

            ApiRes<OrderChkApplyRes> res = mossApiService.OrderChkApply(req);

            if (res.getHead() != null && "000000".equals(res.getHead().getCode())) {
                String downloadUrl = res.getResponse().getDown_load_url();
                log.info("【申请对账单】申请成功, tranDate: {}, downloadUrl: {}", tranDate, downloadUrl);
                return Result.success(downloadUrl, "申请成功");
            } else {
                log.warn("【申请对账单】申请失败: {}", res.getHead() != null ? res.getHead().getDesc() : "未知错误");
                return Result.failed("申请失败: " + (res.getHead() != null ? res.getHead().getDesc() : "未知错误"));
            }
        } catch (Exception e) {
            log.error("【申请对账单】申请异常", e);
            return Result.failed("申请异常: " + e.getMessage());
        }
    }

    @NotNull
    private void calculateCommission(Long id, Double rawAmount) {
        var proUser = proUserMapper.selectById(id);
        proUser.setBalance(proUser.getBalance() + rawAmount * (1 - proUser.getDiscount()));
        proUserMapper.updateById(proUser);

        var newBill = new BillEntity();
        newBill.setOrderNo(String.valueOf(LocalDateTime.now().toInstant(ZoneOffset.ofHours(8)).toEpochMilli()))
                .setType("commission")
                .setUserId(proUser.getId())
                .setActualPayAmount(0 - rawAmount * (1 - proUser.getDiscount()))
                .setState(1)
                .setUpdateTime(LocalDateTime.now());
        billMapper.insert(newBill);
    }
}
