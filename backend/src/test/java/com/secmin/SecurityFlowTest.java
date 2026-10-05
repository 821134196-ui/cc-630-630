package com.secmin;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.config.MultiPartConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全场景集成测试，覆盖验收点：
 *  A. 转岗降级阻止旧链接（正文 + 附件）；
 *  B. 负责人审批撤回阻止旧链接，且历史分发记录保留；
 *  C. 管理员撤销个人授权阻止旧链接；
 *  D. 越权访问：他人链接、未登录、伪造 token、跨版本附件下载均被拒绝；
 *  E. 正文与附件版本一致（同一版本原子发布）；空正文不能提交；
 *  F. 邮件只发提醒，不含纪要正文。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityFlowTest {

    static final String SECRETARY = "chenjing";
    static final String APPROVER = "lifang";
    static final String ADMIN = "admin";
    static final String WANGWEI = "wangwei";   // 机密 4
    static final String ZHAOLEI = "zhaolei";   // 内部 2
    static final String SUNLI = "sunli";       // 秘密 3

    static long minutesId;
    static long v1Id;
    static long v1AttachmentId;
    static String wangweiTokenV1;
    static long wangweiGrantIdV1;

    static long minutes2Id;
    static long otherVersionId;
    static long otherAttachmentId;

    static final String V1_BODY = "【机密】Q4并购讨论，授权额度1.2亿元，敏感正文唯一串ABC123。";
    static final byte[] V1_FILE = "机密附件内容-XYZ789".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @BeforeAll
    static void utf8() {
        // multipart 文本 part 以 UTF-8 编码，避免中文在提交时变 '?'
        RestAssured.config = RestAssuredConfig.config()
                .encoderConfig(EncoderConfig.encoderConfig()
                        .defaultContentCharset("UTF-8")
                        .appendDefaultContentCharsetToContentTypeIfUndefined(false))
                .multiPartConfig(MultiPartConfig.multiPartConfig().defaultCharset("UTF-8"));
    }

    @Test
    @Order(1)
    void t01_secretarySubmitsAtomicVersion_bodyAndAttachment() {
        // 创建纪要
        minutesId = RestAssured.given().header("X-Account", SECRETARY)
                .contentType(ContentType.JSON).body(Map.of("title", "安全测试-并购纪要"))
                .post("/api/minutes").then().statusCode(200)
                .extract().jsonPath().getLong("id");

        // 正文 + 附件 + 建议名单同一次 multipart 提交为一个版本
        Response r = RestAssured.given().header("X-Account", SECRETARY)
                .multiPart("classification", "SECRET")
                .multiPart("body", "body", V1_BODY.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain")
                .multiPart("changeNote", "changeNote", "首次发布".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain")
                .multiPart("recipients", "[3,4]") // 王伟(4 满足)、赵磊(2 不满足机密)
                .multiPart("files", "并购附件.txt", V1_FILE, "text/plain")
                .post("/api/minutes/" + minutesId + "/versions")
                .then().statusCode(200)
                .body("state", equalTo("PENDING"))
                .body("classification", equalTo("SECRET"))
                .extract().response();
        v1Id = r.jsonPath().getLong("id");

        // 版本详情中附件归属同一版本（正文与附件版本一致）
        JsonPath detail = RestAssured.given().header("X-Account", SECRETARY)
                .get("/api/minutes/" + minutesId).then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> versions = detail.getList("versions");
        Map<String, Object> v1 = versions.stream()
                .filter(x -> ((Number) x.get("id")).longValue() == v1Id).findFirst().orElseThrow();
        List<Map<String, Object>> atts = (List<Map<String, Object>>) v1.get("attachments");
        assertEquals(1, atts.size(), "版本应带 1 个附件");
        assertEquals(v1Id, ((Number) atts.get(0).get("versionId")).longValue(),
                "附件必须与正文同属一个版本");
        v1AttachmentId = ((Number) atts.get(0).get("id")).longValue();
        assertEquals(V1_BODY, v1.get("bodyText"), "版本正文一致");
    }

    @Test
    @Order(2)
    void t02_emptyBodyRejected_noPartialVersion() {
        RestAssured.given().header("X-Account", SECRETARY)
                .multiPart("classification", "INTERNAL")
                .multiPart("body", "body", "   ".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain")
                .multiPart("recipients", "[]")
                .post("/api/minutes/" + minutesId + "/versions")
                .then().statusCode(400);
    }

    @Test
    @Order(3)
    void t03_approverPreviewsRecipients_ineligibleFlagged() {
        JsonPath preview = RestAssured.given().header("X-Account", APPROVER)
                .get("/api/minutes/versions/" + v1Id + "/preview")
                .then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> candidates = preview.getList("candidates");
        Map<String, Object> wangwei = byAccount(candidates, WANGWEI);
        Map<String, Object> zhaolei = byAccount(candidates, ZHAOLEI);
        assertEquals(true, wangwei.get("eligible"), "王伟机密密级满足");
        assertEquals(false, zhaolei.get("eligible"), "赵磊内部密级不满足机密");
        assertEquals(true, wangwei.get("proposed"), "王伟在建议名单");
        assertEquals(false, zhaolei.get("proposed"), "赵磊密级不足，不进建议名单");

        // 员工不能审批
        RestAssured.given().header("X-Account", WANGWEI)
                .contentType(ContentType.JSON).body(Map.of("recipientIds", List.of(3)))
                .post("/api/minutes/versions/" + v1Id + "/approve")
                .then().statusCode(403);

        // 把密级不足的人强塞进最终名单，应被拒绝
        RestAssured.given().header("X-Account", APPROVER)
                .contentType(ContentType.JSON).body(Map.of("recipientIds", List.of(3, 4)))
                .post("/api/minutes/versions/" + v1Id + "/approve")
                .then().statusCode(400);
    }

    @Test
    @Order(4)
    void t04_approve_createsGrantAndSendsSafeMail() {
        RestAssured.given().header("X-Account", APPROVER)
                .contentType(ContentType.JSON).body(Map.of("recipientIds", List.of(3)))
                .post("/api/minutes/versions/" + v1Id + "/approve")
                .then().statusCode(200).body("state", equalTo("APPROVED"));

        // 分发记录中取王伟的 token
        JsonPath grants = RestAssured.given().header("X-Account", ADMIN)
                .queryParam("minutesId", minutesId)
                .get("/api/admin/grants").then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> list = grants.getList("$");
        Map<String, Object> g = list.stream()
                .filter(x -> WANGWEI.equals(x.get("userAccount"))
                        && ((Number) x.get("versionId")).longValue() == v1Id)
                .findFirst().orElseThrow();
        wangweiTokenV1 = (String) g.get("token");
        wangweiGrantIdV1 = ((Number) g.get("id")).longValue();
        assertNotNull(wangweiTokenV1);
    }

    @Test
    @Order(5)
    void t05_recipientViewsBodyAndDownloadsAttachment() {
        Response view = RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/" + wangweiTokenV1)
                .then().statusCode(200)
                .body("bodyText", equalTo(V1_BODY))
                .body("classification", equalTo("SECRET"))
                .extract().response();
        List<Map<String, Object>> atts = view.jsonPath().getList("attachments");
        assertEquals(1, atts.size());

        byte[] got = RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/attachment/" + wangweiTokenV1 + "/" + v1AttachmentId)
                .then().statusCode(200)
                .header("Content-Disposition", containsString("attachment"))
                .extract().asByteArray();
        assertArrayEquals(V1_FILE, got, "附件内容一致");
    }

    @Test
    @Order(6)
    void t06_crossUserAndAnonymousAccess_denied() {
        // 其他员工拿王伟的专属链接
        RestAssured.given().header("X-Account", SUNLI)
                .get("/api/access/view/" + wangweiTokenV1)
                .then().statusCode(403).body("denied", equalTo(true));
        // 未登录
        RestAssured.get("/api/access/view/" + wangweiTokenV1)
                .then().statusCode(401);
        // 伪造 token
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/does-not-exist-token")
                .then().statusCode(403);
        // 他人 token 下载附件
        RestAssured.given().header("X-Account", SUNLI)
                .get("/api/access/attachment/" + wangweiTokenV1 + "/" + v1AttachmentId)
                .then().statusCode(403);
    }

    @Test
    @Order(7)
    void t07_crossVersionAttachmentDownload_denied() {
        // 再建一份独立纪要并审批给王伟，拿到它的 token
        minutes2Id = RestAssured.given().header("X-Account", SECRETARY)
                .contentType(ContentType.JSON).body(Map.of("title", "安全测试-独立纪要"))
                .post("/api/minutes").then().statusCode(200).extract().jsonPath().getLong("id");
        Response vr = RestAssured.given().header("X-Account", SECRETARY)
                .multiPart("classification", "INTERNAL")
                .multiPart("body", "body", "另一份纪要正文".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain")
                .multiPart("recipients", "[3]")
                .multiPart("files", "other.txt", "其它附件".getBytes(), "text/plain")
                .post("/api/minutes/" + minutes2Id + "/versions")
                .then().statusCode(200).extract().response();
        otherVersionId = vr.jsonPath().getLong("id");
        RestAssured.given().header("X-Account", APPROVER)
                .contentType(ContentType.JSON).body(Map.of("recipientIds", List.of(3)))
                .post("/api/minutes/versions/" + otherVersionId + "/approve")
                .then().statusCode(200);
        String token2 = tokenOf(minutes2Id, WANGWEI, otherVersionId);
        otherAttachmentId = attachmentIdOf(minutes2Id, otherVersionId);

        // 用纪要2的 token 去读纪要1的附件 —— 必须 403，不能绕过
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/attachment/" + token2 + "/" + v1AttachmentId)
                .then().statusCode(403);
        // 反过来同样拒绝
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/attachment/" + wangweiTokenV1 + "/" + otherAttachmentId)
                .then().statusCode(403);
    }

    @Test
    @Order(8)
    void t08_transferDown_blocksOldLinkImmediately() {
        // 王伟转岗到低密级岗位（内部 2），旧机密链接立即失效
        RestAssured.given().header("X-Account", ADMIN).contentType(ContentType.JSON)
                .body(Map.of("post", "行政后勤部", "clearanceLevel", 2))
                .post("/api/users/3/transfer")
                .then().statusCode(200).body("clearanceLevel", equalTo(2));

        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/" + wangweiTokenV1)
                .then().statusCode(403)
                .body("message", containsString("低于纪要密级"));
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/attachment/" + wangweiTokenV1 + "/" + v1AttachmentId)
                .then().statusCode(403);
    }

    @Test
    @Order(9)
    void t09_transferBack_restoresAccessByLiveCheck() {
        RestAssured.given().header("X-Account", ADMIN).contentType(ContentType.JSON)
                .body(Map.of("post", "战略规划部", "clearanceLevel", 4))
                .post("/api/users/3/transfer")
                .then().statusCode(200);
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/" + wangweiTokenV1)
                .then().statusCode(200);
    }

    @Test
    @Order(10)
    void t10_adminRevokesGrant_blocksOldLink_butRecordKept() {
        RestAssured.given().header("X-Account", ADMIN)
                .post("/api/admin/grants/" + wangweiGrantIdV1 + "/revoke")
                .then().statusCode(200);

        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/" + wangweiTokenV1)
                .then().statusCode(403)
                .body("message", containsString("授权已被撤销"));
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/attachment/" + wangweiTokenV1 + "/" + v1AttachmentId)
                .then().statusCode(403);

        // 历史分发记录仍保留，且标记 revoked
        JsonPath grants = RestAssured.given().header("X-Account", ADMIN)
                .queryParam("minutesId", minutesId)
                .get("/api/admin/grants").then().statusCode(200).extract().jsonPath();
        boolean kept = grants.<Map<String, Object>>getList("$").stream()
                .anyMatch(x -> ((Number) x.get("id")).longValue() == wangweiGrantIdV1
                        && Boolean.TRUE.equals(x.get("revoked")));
        assertTrue(kept, "撤销后历史分发记录仍保留");
    }

    @Test
    @Order(11)
    void t11_newVersionApprovedThenWithdrawn_blocksOldLink_recordKept() {
        // 提交 v2 并审批给王伟（产生新授权）；v1 变 SUPERSEDED
        Response vr = RestAssured.given().header("X-Account", SECRETARY)
                .multiPart("classification", "CONFIDENTIAL")
                .multiPart("body", "body", "v2 修订正文-预算调整".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain")
                .multiPart("changeNote", "changeNote", "修订预算".getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/plain")
                .multiPart("recipients", "[3,5]")
                .multiPart("files", "v2附件.txt", "v2附件内容".getBytes(), "text/plain")
                .post("/api/minutes/" + minutesId + "/versions")
                .then().statusCode(200).extract().response();
        long v2Id = vr.jsonPath().getLong("id");
        RestAssured.given().header("X-Account", APPROVER)
                .contentType(ContentType.JSON).body(Map.of("recipientIds", List.of(3, 5)))
                .post("/api/minutes/versions/" + v2Id + "/approve")
                .then().statusCode(200);
        String tokenV2 = tokenOf(minutesId, WANGWEI, v2Id);

        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/" + tokenV2).then().statusCode(200);

        // 负责人撤回 v2 审批
        RestAssured.given().header("X-Account", APPROVER)
                .post("/api/minutes/versions/" + v2Id + "/withdraw")
                .then().statusCode(200).body("state", equalTo("WITHDRAWN"));

        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/view/" + tokenV2)
                .then().statusCode(403)
                .body("message", containsString("撤回"));
        RestAssured.given().header("X-Account", WANGWEI)
                .get("/api/access/attachment/" + tokenV2 + "/" + attachmentIdOf(minutesId, v2Id))
                .then().statusCode(403);

        // 已撤回不能重复撤回
        RestAssured.given().header("X-Account", APPROVER)
                .post("/api/minutes/versions/" + v2Id + "/withdraw")
                .then().statusCode(409);

        // 历史记录仍在，版本状态为 WITHDRAWN
        JsonPath grants = RestAssured.given().header("X-Account", ADMIN)
                .queryParam("minutesId", minutesId)
                .get("/api/admin/grants").then().extract().jsonPath();
        boolean kept = grants.<Map<String, Object>>getList("$").stream()
                .anyMatch(x -> WANGWEI.equals(x.get("userAccount"))
                        && ((Number) x.get("versionId")).longValue() == v2Id
                        && "WITHDRAWN".equals(x.get("versionState")));
        assertTrue(kept, "撤回后分发历史仍保留并标注 WITHDRAWN");
    }

    @Test
    @Order(12)
    void t12_mailsNeverContainBody_andDenialsAudited() {
        // 所有模拟邮件都不得包含纪要正文
        JsonPath mails = RestAssured.given().header("X-Account", ADMIN)
                .get("/api/admin/mails").then().statusCode(200).extract().jsonPath();
        for (Map<String, Object> m : mails.<Map<String, Object>>getList("$")) {
            assertEquals(false, m.get("containsBody"),
                    "邮件不得包含正文：" + m.get("subject"));
            String body = (String) m.get("bodyText");
            assertFalse(body != null && body.contains("敏感正文唯一串ABC123"),
                    "提醒邮件不得夹带敏感正文");
        }

        // 审计中必须出现拒绝记录（转岗/撤回/越权），动作与原因齐全
        JsonPath audit = RestAssured.given().header("X-Account", ADMIN)
                .queryParam("result", "DENIED")
                .get("/api/admin/audit").then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> denied = audit.getList("$");
        assertFalse(denied.isEmpty(), "应至少有一条拒绝审计");
        assertTrue(denied.stream().anyMatch(d -> WANGWEI.equals(d.get("userAccount"))),
                "审计记录到具体人员");
        // 覆盖各类拒绝原因
        List<String> reasons = denied.stream()
                .map(d -> (String) d.get("reason")).filter(Objects::nonNull).toList();
        assertTrue(reasons.stream().anyMatch(r -> r.contains("密级")), "存在转岗密级拒绝记录");
        assertTrue(reasons.stream().anyMatch(r -> r.contains("撤回")), "存在审批撤回拒绝记录");
        assertTrue(reasons.stream().anyMatch(r -> r.contains("授权已被撤销")), "存在授权撤销拒绝记录");
    }

    // ---------- helpers ----------

    private static Map<String, Object> byAccount(List<Map<String, Object>> users, String account) {
        return users.stream().filter(u -> account.equals(u.get("account"))).findFirst()
                .orElseThrow(() -> new AssertionError("缺少用户 " + account));
    }

    private static String tokenOf(long mid, String account, long versionId) {
        JsonPath grants = RestAssured.given().header("X-Account", ADMIN)
                .queryParam("minutesId", mid)
                .get("/api/admin/grants").then().statusCode(200).extract().jsonPath();
        return (String) grants.<Map<String, Object>>getList("$").stream()
                .filter(g -> account.equals(g.get("userAccount"))
                        && ((Number) g.get("versionId")).longValue() == versionId)
                .findFirst().orElseThrow(() -> new AssertionError("找不到授权"))
                .get("token");
    }

    private static long attachmentIdOf(long minutesId, long versionId) {
        JsonPath detail = RestAssured.given().header("X-Account", SECRETARY)
                .get("/api/minutes/" + minutesId)
                .then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> versions = detail.getList("versions");
        Map<String, Object> v = versions.stream()
                .filter(x -> ((Number) x.get("id")).longValue() == versionId)
                .findFirst().orElseThrow(() -> new AssertionError("版本不存在"));
        List<Map<String, Object>> atts = (List<Map<String, Object>>) v.get("attachments");
        return ((Number) atts.get(0).get("id")).longValue();
    }
}
