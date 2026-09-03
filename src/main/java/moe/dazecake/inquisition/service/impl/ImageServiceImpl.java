package moe.dazecake.inquisition.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.qiniu.common.QiniuException;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;

import moe.dazecake.inquisition.service.intf.ImageService;
import moe.dazecake.inquisition.utils.Result;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import okhttp3.*;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Date;

@Service
public class ImageServiceImpl implements ImageService {

    @Value("${storage.oss.enable:false}")
    private boolean ossEnable;

    @Value("${storage.oss.secretId:}")
    private String secretId;

    @Value("${storage.oss.secretKey:}")
    private String secretKey;

    @Value("${storage.oss.bucket:}")
    private String bucketName;

    @Value("${storage.oss.region:}")
    private String regionName;

    @Value("${storage.chfs.enable:false}")
    private boolean chfsEnable;

    @Value("${storage.chfs.url:}")
    private String chfsUrl;

    @Value("${storage.chfs.username:}")
    private String chfsUsername;

    @Value("${storage.chfs.password:}")
    private String chfsPassword;

    @Value("${storage.chfs.uploadDir:}")
    private String chfsUploadDir;

    @Value("${storage.qiniu.enable:false}")
    private boolean qiniuEnable;

    @Value("${storage.qiniu.accessKey:}")
    private String qiniuAccessKey;

    @Value("${storage.qiniu.secretKey:}")
    private String qiniuSecretKey;

    @Value("${storage.qiniu.bucket:}")
    private String qiniuBucket;

    @Value("${storage.qiniu.domain:}")
    private String qiniuDomain;

    @Override
    public Result<String> uploadImage(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return Result.failed("图片内容为空");
        }
        // M5 修复：校验Base64大小，限制为5MB以内
        if (base64Image.length() > 5 * 1024 * 1024) {
            return Result.failed("图片过大，限制为5MB以内");
        }
        if (ossEnable) {
            return uploadImageToCos(base64Image);
        } else if (qiniuEnable) {
            return uploadImageToQiniu(base64Image);
        } else if (chfsEnable) {
            return uploadImageToCHFS(base64Image);
        } else {
            return Result.failed("未配置任何存储服务");
        }
    }
    
    private Result<String> uploadImageToCos(String base64Image) {
        COSClient cosClient = new COSClient(
            new BasicCOSCredentials(secretId, secretKey),
            new ClientConfig(new Region(regionName))
        );
        try {
            var fileName = String.valueOf(System.currentTimeMillis());
            var file = File.createTempFile(fileName, ".png");
            var fos = new FileOutputStream(file);
            //base64解码并写入文件
            fos.write(Base64.decodeBase64(stripDataUriPrefix(base64Image)));
            fos.flush();
            fos.close();

            //上传至 COS
            PutObjectRequest objectRequest = new PutObjectRequest(bucketName, fileName + ".png", file);
            cosClient.putObject(objectRequest);

            //获取下载地址
            Date expirationDate = new Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000);

            HttpMethodName method = HttpMethodName.GET;

            URL url = cosClient.generatePresignedUrl(bucketName, fileName + ".png", expirationDate, method);

            return Result.success(url.toString(), "上传成功");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            cosClient.shutdown();
        }
    }

    /**
     * 上传图片到七牛云 Kodo 对象存储
     */
    private Result<String> uploadImageToQiniu(String base64Image) {
        // 配置完整性校验，避免后续异常导致 500
        if (isBlank(qiniuAccessKey) || isBlank(qiniuSecretKey) || isBlank(qiniuBucket) || isBlank(qiniuDomain)) {
            return Result.failed("七牛云存储配置不完整，请检查 accessKey、secretKey、bucket、domain 是否已配置");
        }
        try {
            // 构造鉴权对象
            Auth auth = Auth.create(qiniuAccessKey, qiniuSecretKey);

            // 使用自动区域配置，SDK 会根据 bucket 自动探测所属区域
            Configuration cfg = Configuration.create(com.qiniu.storage.Region.autoRegion());
            UploadManager uploadManager = new UploadManager(cfg);

            var fileName = String.valueOf(System.currentTimeMillis());
            // 解码 base64 为字节数组
            byte[] imageBytes = Base64.decodeBase64(stripDataUriPrefix(base64Image));

            // 生成上传凭证
            String upToken = auth.uploadToken(qiniuBucket, fileName + ".png");

            // 调用 put 方法上传（参数：字节数组、key、上传凭证）
            com.qiniu.http.Response response = uploadManager.put(imageBytes, fileName + ".png", upToken);

            // 先判断响应是否成功，再解析 JSON，避免 response 异常时 NPE
            if (!response.isOK()) {
                return Result.failed("七牛云上传失败: " + response.error);
            }
            DefaultPutRet putRet = response.jsonToObject(DefaultPutRet.class);

            // 返回下载地址（拼接域名 + key）
            String domain = qiniuDomain.trim();
            if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
                domain = "https://" + domain;
            }
            String fileUrl = domain.replaceAll("/+$", "") + "/" + putRet.key;
            return Result.success(fileUrl, "上传成功");
        } catch (QiniuException e) {
            return Result.failed("七牛云上传异常: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.failed("七牛云配置参数不合法: " + e.getMessage());
        }
    }

    /**
     * 去除 base64 字符串可能携带的 Data URI 前缀（如 data:image/png;base64,）
     */
    private String stripDataUriPrefix(String base64Image) {
        int commaIndex = base64Image.indexOf(',');
        if (commaIndex > -1 && base64Image.substring(0, commaIndex).matches("^data:[^;]+;base64$")) {
            return base64Image.substring(commaIndex + 1);
        }
        return base64Image;
    }

    /**
     * 判断字符串是否为空或空白
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    private Result<String> uploadImageToCHFS(String base64Image) {
        OkHttpClient client = new OkHttpClient();
        // 登录 CHFS
        var loginUrl = chfsUrl + "/chfs/session";
        var loginRequestBody = new FormBody.Builder()
                .add("user", chfsUsername)
                .add("pwd", chfsPassword)
                .build();
        var loginRequest = new Request.Builder()
                .url(loginUrl)
                .post(loginRequestBody)
                .build();
        try (Response loginResponse = client.newCall(loginRequest).execute()) {
            int statusCode = loginResponse.code();
            if (statusCode != 201) {
                return Result.failed("登录失败，返回码为 " + statusCode);
            }
            var cookie = loginResponse.headers("Set-Cookie");
            if (cookie == null || cookie.size() < 2) {
                return Result.failed("登录失败，无法获取 Cookie");
            }
            //正则匹配COOKIE中JWT字段后的cookie
            var jwtPattern = Pattern.compile("JWT=([^;]+)");
            String jwt = null;
            for (String c : cookie) {
                var jwtMatcher = jwtPattern.matcher(c);
                if (jwtMatcher.find()) {
                    jwt = jwtMatcher.group(1);
                }
            }

            // 创建图片临时文件
            var fileName = String.valueOf(System.currentTimeMillis());
            var file = File.createTempFile(fileName, ".png");
            var fos = new FileOutputStream(file);
            //base64解码并写入文件
            fos.write(Base64.decodeBase64(stripDataUriPrefix(base64Image)));
            fos.flush();
            fos.close();
            // 上传至 CHFS
            var uploadUrl = chfsUrl + "/chfs/upload";
            var uploadRequestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName + ".png", RequestBody.create(file, MediaType.parse("image/png")))
                    .addFormDataPart("folder", chfsUploadDir)
                    .build();
            var uploadRequest = new Request.Builder()
                .url(uploadUrl)
                .post((RequestBody) uploadRequestBody)
                .addHeader("Cookie", "JWT=" + jwt + "; user=" + chfsUsername)
                .build();
            try (Response uploadResponse = client.newCall(uploadRequest).execute()) {
                int uploadStatusCode = uploadResponse.code();
                if (uploadStatusCode != 201) {
                    return Result.failed("上传失败，返回码不为 201");
                }
                var downloadUrl = chfsUrl + "/shared" + chfsUploadDir + "/" + fileName + ".png";
                return Result.success(downloadUrl.toString(), "上传成功");
            }
        }catch (IOException e) {
        throw new RuntimeException(e);
        }
    }
}
