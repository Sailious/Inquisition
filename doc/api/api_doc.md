# 📋 Inquisition API 文档 | v1.3.1

> 由 springdoc-openapi 运行时提取 + 按代码鉴权注解（@Login/@UserLogin/@ProUserLogin）校正生成。可直接导入 Apifox（OpenAPI 3.0）。

## 概述

| 属性 | 值 |
|------|-----|
| API 名称 | Inquisition（审判庭）|
| 版本 | v1.3.1 |
| Base URL | http://localhost:2000 |
| 认证方式 | Bearer JWT，分 3 种角色 |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

## 鉴权与角色说明

除「公开」接口外，所有请求需在 Header 中携带：

```http
Authorization: Bearer <your_jwt_token>
```

系统有 3 种 JWT 角色（token 类型），分别由登录接口获取：

| 角色 | 获取接口 | 说明 |
|------|----------|------|
| **admin**（管理员）| `POST /adminLogin` | 平台超级管理员，可管理账号/设备/CDK/商品/日志/公告/统计/任务，以及创建与管理高级用户(代理商) |
| **user**（普通用户）| `POST /userLogin` | 终端用户，管理自己的账号、日志、作战控制等 |
| **proUser**（高级用户/代理商）| `POST /proUserLogin` | 代理商，管理自己的附属用户(subUser)、CDK、余额等 |

> **关于文档分组**：接口按模块（控制器 `@Tag`）分组，因此管理员可操作的「高级用户管理」接口（如 `GET /getAllProUser`、`POST /createProUser`）写在「高级用户接口」分组下，而非「管理员接口」分组。要看某个角色能调哪些接口，请直接看下方「[按角色可调用接口](#按角色可调用接口)」索引。

## 接口文档（按模块）

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
### 用户接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/userLogin` | 登陆我的账号 | 🔓 公开 |
| POST | `/useCDK` | 使用CDK | 🔒 user |
| POST | `/updateMyAccount` | 更新自己的账号 | 🔒 user |
| POST | `/updateAccountAndPassword` | 更新账号密码 | 🔒 user |
| POST | `/unfreezeMyAccount` | 解冻我的账号 | 🔒 user |
| POST | `/startNow` | 立即开始作战 | 🔒 user |
| POST | `/getWechatCallback` | 获取微信推送回调 | 🔓 公开 |
| POST | `/freezeMyAccount` | 冻结我的账号 | 🔒 user |
| POST | `/forceHalt` | 强制停止作战 | 🔒 user |
| POST | `/createUserByCDK` | 使用CDK创建我的账号 | 🔓 公开 |
| GET | `/showMyStatus` | 查询我状态 | 🔒 user |
| GET | `/showMySan` | 查询当前理智 | 🔒 user |
| GET | `/showMyLog` | 查询我的日志 | 🔒 user |
| GET | `/showMyAccount` | 查询自己的账号 | 🔒 user |
| GET | `/getWechatQRCode` | 获取微信推送二维码 | 🔒 user |
| GET | `/getRefresh` | 获取刷新次数 | 🔒 user |

### 日志接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/uploadImage` | 上传图片 | 🔓 公开 |
| POST | `/delLog` | 删除日志 | 🔒 admin |
| POST | `/addLog` | 增加日志 | 🔓 公开 |
| GET | `/showLog` | 查询日志 | 🔒 admin |
| GET | `/searchLogByAccount` | 精确查询账号日志 | 🔒 admin |

### 高级用户接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/updateProUser` | 更新高级用户账号 | 🔒 admin |
| POST | `/updateProUserPassword` | 修改高级用户密码 | 🔒 proUser |
| POST | `/setSubUser` | 代理商配置附属用户设置 | 🔒 proUser |
| POST | `/renewSubUserDaily` | pro_user手动续期附属用户daily时长 | 🔒 proUser |
| POST | `/proUserLogin` | 登陆高级用户账号 | 🔓 公开 |
| POST | `/forceSubUserStop` | 强制附属用户立即停止作战 | 🔒 proUser |
| POST | `/forceSubUserFight` | 强制附属用户立即作战 | 🔒 proUser |
| POST | `/deleteAndRecycleUser` | 删除并回收用户 | 🔒 proUser |
| POST | `/createSubUserByProUser` | pro_user扣除余额创建用户 | 🔒 proUser |
| POST | `/createProUser` | 创建高级用户账号 | 🔒 admin |
| POST | `/createCdkByProUser` | pro_user扣除余额创建cdk | 🔒 proUser |
| POST | `/buyGoodsForSubUser` | pro_user代购商品 | 🔒 proUser |
| POST | `/activateSubUserCdk` | 为附属用户激活CDK | 🔒 proUser |
| GET | `/searchSubUserByAccount` | 通过账号搜索附属用户 | 🔒 proUser |
| GET | `/getSubUserLog` | 显示代理商附属用户日志 | 🔒 proUser |
| GET | `/getSubUserList` | 分页显示代理商的附属用户 | 🔒 proUser |
| GET | `/getRecentlyExpiredUsers` | 获取近期到期的附属用户 | 🔒 proUser |
| GET | `/getProUserInventoryCdk` | 显示pro_user库存CDK | 🔒 proUser |
| GET | `/getProUserInfo` | 获取高级用户信息 | 🔒 proUser |
| GET | `/getAllProUser` | 分页查询高级用户账号 | 🔒 admin |

### 商品接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/updateGoods` | 更新商品 | 🔒 admin |
| POST | `/addGoods` | 增加商品 | 🔒 admin |
| GET | `/getGoodsList` | 获取商品列表 | 🔓 公开 |
| GET | `/getGoodsListByAdmin` | 获取商品列表(admin) | 🔒 admin |

### 设备接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/updateDevice` | 更新设备 | 🔒 admin |
| POST | `/isScopeDeviceFree` | 获取域设备是否存在空闲 | 🔓 公开 |
| POST | `/delDevice` | 删除设备 | 🔒 admin |
| POST | `/addDevice` | 增加设备 | 🔒 admin |
| GET | `/showLoadedDevice` | 查询已载入设备 | 🔒 admin |
| GET | `/showInventoryDevice` | 分页查询库存设备 | 🔒 admin |
| GET | `/getDeviceByToken` | 通过设备token获取设备信息 | 🔒 admin |

### 账号接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/updateAccount` | 更新账号 | 🔒 admin |
| POST | `/transferAccountFromArkLights` | 从速通迁移账号 | 🔒 admin |
| POST | `/startAccountByAdmin` | 账号立即作战 | 🔒 admin |
| POST | `/resetRefresh` | 重置刷新次数 | 🔒 admin |
| POST | `/resetAccountDynamicInfo` | 重置账号动态信息 | 🔒 admin |
| POST | `/delAccount` | 删除账号 | 🔒 admin |
| POST | `/addAccount` | 增加账号 | 🔒 admin |
| GET | `/showUserStatus` | 查询用户状态 | 🔒 admin |
| GET | `/showUserSan` | 查询用户当前理智 | 🔒 admin |
| GET | `/showAccount` | 分页查询账号 | 🔒 admin |
| GET | `/searchAccount` | 搜索账号 | 🔒 admin |

### 任务接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/tempRemoveTask` | 临时移除任务 | 🔒 admin |
| POST | `/tempInsertTask` | 临时插队任务 | 🔒 admin |
| POST | `/forceUnlockTaskList` | 立即强制释放整个上锁队列 | 🔒 admin |
| POST | `/forceUnlockOneTask` | 立即强制释放一设备的上锁任务 | 🔒 admin |
| POST | `/forceLoadAllTask` | 立即从数据库重载全部任务 | 🔒 admin |
| POST | `/failTask` | 任务失败上报 | 🔓 公开 |
| POST | `/completeTask` | 完成任务上报 | 🔓 公开 |
| GET | `/showLockTaskList` | 查询已分配任务列表 | 🔒 admin |
| GET | `/showFreezeTaskList` | 查询已冻结任务列表 | 🔒 admin |
| GET | `/showFreeTaskList` | 查询待分配任务列表 | 🔒 admin |
| GET | `/getTask` | 获取任务 | 🔓 公开 |

### 管理员接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/setServerStatus` | 设置服务器启用状态 | 🔒 admin |
| POST | `/changeAdminPassword` | 修改管理员密码 | 🔒 admin |
| POST | `/adminLogin` | 管理员登陆 | 🔓 公开 |
| POST | `/addBalanceForProUser` | 为pro_user增加余额 | 🔒 admin |
| GET | `/getServerStatus` | 获取服务器启用状态 | 🔒 admin |

### 理智接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/sanReport` | 理智上报 | 🔓 公开 |

### 心跳接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/heartBeat` | 心跳协议 | 🔓 公开 |
| POST | `/haltComplete` | 完成停机上报 | 🔓 公开 |

### CDK接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/createCDK` | 批量创建cdk | 🔒 admin |
| GET | `/checkCDKByType` | 通过类型检查库存cdk | 🔒 admin |
| GET | `/checkCDKByTag` | 通过tag检查库存cdk | 🔒 admin |

### 公告接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/createAnnouncement` | 创建一条公告 | 🔒 admin |
| GET | `/getAnnouncement` | 获取公告 | 🔓 公开 |

### 统计接口

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| GET | `/getStatistics` | 获取概览统计数据 | 🔒 admin |

## 按角色可调用接口

> 这份索引按「谁能调用」组织，便于快速核对权限。共 88 个接口：🔒 admin 43 个、🔒 user 13 个、🔒 proUser 16 个、🔓 公开 16 个。

### 🔒 admin 管理员(admin)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/updateProUser` | 更新高级用户账号 |
| POST | `/updateGoods` | 更新商品 |
| POST | `/updateDevice` | 更新设备 |
| POST | `/updateAccount` | 更新账号 |
| POST | `/transferAccountFromArkLights` | 从速通迁移账号 |
| POST | `/tempRemoveTask` | 临时移除任务 |
| POST | `/tempInsertTask` | 临时插队任务 |
| POST | `/startAccountByAdmin` | 账号立即作战 |
| POST | `/setServerStatus` | 设置服务器启用状态 |
| POST | `/resetRefresh` | 重置刷新次数 |
| POST | `/resetAccountDynamicInfo` | 重置账号动态信息 |
| POST | `/forceUnlockTaskList` | 立即强制释放整个上锁队列 |
| POST | `/forceUnlockOneTask` | 立即强制释放一设备的上锁任务 |
| POST | `/forceLoadAllTask` | 立即从数据库重载全部任务 |
| POST | `/delLog` | 删除日志 |
| POST | `/delDevice` | 删除设备 |
| POST | `/delAccount` | 删除账号 |
| POST | `/createProUser` | 创建高级用户账号 |
| POST | `/createCDK` | 批量创建cdk |
| POST | `/createAnnouncement` | 创建一条公告 |
| POST | `/changeAdminPassword` | 修改管理员密码 |
| POST | `/addGoods` | 增加商品 |
| POST | `/addDevice` | 增加设备 |
| POST | `/addBalanceForProUser` | 为pro_user增加余额 |
| POST | `/addAccount` | 增加账号 |
| GET | `/showUserStatus` | 查询用户状态 |
| GET | `/showUserSan` | 查询用户当前理智 |
| GET | `/showLog` | 查询日志 |
| GET | `/showLockTaskList` | 查询已分配任务列表 |
| GET | `/showLoadedDevice` | 查询已载入设备 |
| GET | `/showInventoryDevice` | 分页查询库存设备 |
| GET | `/showFreezeTaskList` | 查询已冻结任务列表 |
| GET | `/showFreeTaskList` | 查询待分配任务列表 |
| GET | `/showAccount` | 分页查询账号 |
| GET | `/searchLogByAccount` | 精确查询账号日志 |
| GET | `/searchAccount` | 搜索账号 |
| GET | `/getStatistics` | 获取概览统计数据 |
| GET | `/getServerStatus` | 获取服务器启用状态 |
| GET | `/getGoodsListByAdmin` | 获取商品列表(admin) |
| GET | `/getDeviceByToken` | 通过设备token获取设备信息 |
| GET | `/getAllProUser` | 分页查询高级用户账号 |
| GET | `/checkCDKByType` | 通过类型检查库存cdk |
| GET | `/checkCDKByTag` | 通过tag检查库存cdk |

### 🔒 user 普通用户(user)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/useCDK` | 使用CDK |
| POST | `/updateMyAccount` | 更新自己的账号 |
| POST | `/updateAccountAndPassword` | 更新账号密码 |
| POST | `/unfreezeMyAccount` | 解冻我的账号 |
| POST | `/startNow` | 立即开始作战 |
| POST | `/freezeMyAccount` | 冻结我的账号 |
| POST | `/forceHalt` | 强制停止作战 |
| GET | `/showMyStatus` | 查询我状态 |
| GET | `/showMySan` | 查询当前理智 |
| GET | `/showMyLog` | 查询我的日志 |
| GET | `/showMyAccount` | 查询自己的账号 |
| GET | `/getWechatQRCode` | 获取微信推送二维码 |
| GET | `/getRefresh` | 获取刷新次数 |

### 🔒 proUser 高级用户(proUser)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/updateProUserPassword` | 修改高级用户密码 |
| POST | `/setSubUser` | 代理商配置附属用户设置 |
| POST | `/renewSubUserDaily` | pro_user手动续期附属用户daily时长 |
| POST | `/forceSubUserStop` | 强制附属用户立即停止作战 |
| POST | `/forceSubUserFight` | 强制附属用户立即作战 |
| POST | `/deleteAndRecycleUser` | 删除并回收用户 |
| POST | `/createSubUserByProUser` | pro_user扣除余额创建用户 |
| POST | `/createCdkByProUser` | pro_user扣除余额创建cdk |
| POST | `/buyGoodsForSubUser` | pro_user代购商品 |
| POST | `/activateSubUserCdk` | 为附属用户激活CDK |
| GET | `/searchSubUserByAccount` | 通过账号搜索附属用户 |
| GET | `/getSubUserLog` | 显示代理商附属用户日志 |
| GET | `/getSubUserList` | 分页显示代理商的附属用户 |
| GET | `/getRecentlyExpiredUsers` | 获取近期到期的附属用户 |
| GET | `/getProUserInventoryCdk` | 显示pro_user库存CDK |
| GET | `/getProUserInfo` | 获取高级用户信息 |

### 🔓 公开 公开(无需认证)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/userLogin` | 登陆我的账号 |
| POST | `/uploadImage` | 上传图片 |
| POST | `/sanReport` | 理智上报 |
| POST | `/proUserLogin` | 登陆高级用户账号 |
| POST | `/isScopeDeviceFree` | 获取域设备是否存在空闲 |
| POST | `/heartBeat` | 心跳协议 |
| POST | `/haltComplete` | 完成停机上报 |
| POST | `/getWechatCallback` | 获取微信推送回调 |
| POST | `/failTask` | 任务失败上报 |
| POST | `/createUserByCDK` | 使用CDK创建我的账号 |
| POST | `/completeTask` | 完成任务上报 |
| POST | `/adminLogin` | 管理员登陆 |
| POST | `/addLog` | 增加日志 |
| GET | `/getTask` | 获取任务 |
| GET | `/getGoodsList` | 获取商品列表 |
| GET | `/getAnnouncement` | 获取公告 |

## 错误码

统一响应体：`{ "code": int, "msg": string, "data": object|null }`。

| HTTP 状态码 | 含义 | 触发场景 |
|------------|------|----------|
| 200 | 成功 | 业务正常返回（业务失败也可能返回 200，需用 `code` 判断）|
| 400 | 参数错误 | 请求体/参数校验失败 |
| 401 | 未认证 | 缺失或无效 JWT |
| 403 | 无权限 | ProKey 校验失败 / 角色不符 |
| 404 | 不存在 | 资源未找到 |
| 500 | 服务器错误 | 未捕获异常 |

## 调用示例（cURL）

### 管理员登录（公开）
```bash
curl -X POST "http://localhost:2000/adminLogin" \
  -H "Content-Type: application/json" \
  -d '{"username":"root","password":"<root_password>"}'
```

### 管理员查询全部高级用户（需 admin token）
```bash
curl -X GET "http://localhost:2000/getAllProUser?current=1&size=10" \
  -H "Authorization: Bearer <admin_token>"
```

### 普通用户登录（公开）
```bash
curl -X POST "http://localhost:2000/userLogin" \
  -H "Content-Type: application/json" \
  -d '{"account":"<account>","password":"<password>"}'
```

### 高级用户登录（公开）
```bash
curl -X POST "http://localhost:2000/proUserLogin" \
  -H "Content-Type: application/json" \
  -d '{"account":"<agent_account>","password":"<password>"}'
```

### 设备拉取任务（公开，设备方调用）
```bash
curl -X GET "http://localhost:2000/getTask?deviceToken=<device_token>"
```

## SDK 示例

### Python
```python
import requests
BASE = "http://localhost:2000"
class InquisitionClient:
    def __init__(self, token=None):
        self.session = requests.Session()
        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"
        self.session.headers["Content-Type"] = "application/json"
    def admin_login(self, username, password):
        return self.session.post(f"{BASE}/adminLogin", json={"username": username, "password": password}).json()
    def get_all_pro_user(self, current=1, size=10):  # 需 admin token
        return self.session.get(f"{BASE}/getAllProUser", params={"current": current, "size": size}).json()
    def get_task(self, device_token):  # 公开
        return requests.get(f"{BASE}/getTask", params={"deviceToken": device_token}).json()
```

### JavaScript
```javascript
class InquisitionClient {
  constructor(token) {
    this.base = "http://localhost:2000";
    this.headers = { "Content-Type": "application/json" };
    if (token) this.headers["Authorization"] = `Bearer ${token}`;
  }
  async adminLogin(username, password) {
    const r = await fetch(`${this.base}/adminLogin`, { method: "POST", headers: this.headers, body: JSON.stringify({ username, password }) });
    return r.json();
  }
  async getAllProUser(current = 1, size = 10) {  // 需 admin token
    const r = await fetch(`${this.base}/getAllProUser?current=${current}&size=${size}`, { headers: this.headers });
    return r.json();
  }
}
```

## 导入 Apifox

1. 打开 Apifox → 项目 → **导入**
2. 选择 **OpenAPI / Swagger** → 上传 `doc/api/openapi.json`
3. 每个接口的「权限/角色」见 `x-required-role` 字段与接口描述首行标注；安全方案为 Bearer JWT。

---
* 共 88 个接口：🔒 admin 43、🔒 user 13、🔒 proUser 16、🔓 公开 16。*