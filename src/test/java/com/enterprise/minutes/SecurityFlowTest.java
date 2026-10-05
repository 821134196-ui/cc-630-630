package com.enterprise.minutes;

import com.enterprise.minutes.domain.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.config.EncoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.MultiPartSpecification;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * 安全关键场景端到端测试：
 * 1) 正常授权访问（正文+附件）；
 * 2) 转岗后旧链接立即失效（正文与下载都拒绝）；
 * 3) 负责人审批撤回后旧链接立即失效；
 * 4) 正文与附件作为同一版本发布，修订后旧附件无法通过新链接读取；
 * 5) 越权下载（无授权员工/伪造令牌/他人令牌）被拒绝并审计；
 * 6) 提醒邮件不含敏感正文。
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityFlowTest {

    /** multipart 文本字段强制 UTF-8，避免中文正文被编码成问号 */
    static final RestAssuredConfig UTF8 = RestAssured.config()
            .encoderConfig(new EncoderConfig()
                    .defaultContentCharset(StandardCharsets.UTF_8.name())
                    .appendDefaultContentCharsetToContentTypeIfUndefined(false));

    static final String SENSITIVE_BODY = "【绝密正文】SECRET_PAYLOAD_2026 并购定价底线";

    static Long minutesId;
    static String zhangToken;
    static Long firstAttachmentId;
    static Long secondAttachmentId;

    static Map<String, String> auth(String username) {
        return Map.of("X-Username", username);
    }

    static File tmpFile(String name, String content) throws Exception {
        File f = File.createTempFile(name, ".txt");
        f.deleteOnExit();
        java.nio.file.Files.writeString(f.toPath(), content, StandardCharsets.UTF_8);
        return f;
    }

    /** multipart 文本部件，显式 UTF-8（容器默认字符集可能是 ASCII） */
    static MultiPartSpecification textPart(String name, String value) {
        return new MultiPartSpecBuilder(value)
                .controlName(name)
                .charset(StandardCharsets.UTF_8)
                .mimeType("text/plain")
                .build();
    }

    @Test
    @Order(1)
    void step01_recorderCreatesDraftWithBodyAndAttachments() throws Exception {
        Response r = given().config(UTF8)
                .header("X-Username", "recorder")
                .multiPart(textPart("title", "收购标的评审纪要"))
                .multiPart(textPart("classification", "SECRET"))
                .multiPart(textPart("body", SENSITIVE_BODY))
                .multiPart("files", tmpFile("v1-附件", "v1 附件内容"), "application/octet-stream")
                .contentType("multipart/form-data")
                .when().post("/api/minutes")
                .then().statusCode(200)
                .body("classification", equalTo("SECRET"))
                .body("status", equalTo("DRAFT"))
                .body("currentVersionNo", nullValue())
                .extract().response();
        minutesId = r.jsonPath().getLong("id");

        // 无附件的提交必须被拒绝：强制正文+附件同一版本
        given()
                .header("X-Username", "recorder")
                .multiPart(textPart("title", "残缺版本"))
                .multiPart(textPart("classification", "INTERNAL"))
                .multiPart(textPart("body", "只有正文"))
                .contentType("multipart/form-data")
                .when().post("/api/minutes")
                .then().statusCode(400);
    }

    @Test
    @Order(2)
    void step02_cannotProposeUnderClearanceRecipient() {
        // chen 资质 2 级 < SECRET(3)：不允许列入候选名单
        given().headers(auth("recorder")).contentType(ContentType.JSON)
                .body(Map.of("usernames", List.of("zhang", "chen")))
                .when().post("/api/minutes/" + minutesId + "/submit")
                .then().statusCode(400);

        // zhang(3级)、liu(4级) 达标
        given().headers(auth("recorder")).contentType(ContentType.JSON)
                .body(Map.of("usernames", List.of("zhang", "liu")))
                .when().post("/api/minutes/" + minutesId + "/submit")
                .then().statusCode(200);
    }

    @Test
    @Order(3)
    void step03_approverPreviewsAndPublishes() {
        given().headers(auth("approver"))
                .when().get("/api/minutes/" + minutesId + "/approval-preview")
                .then().statusCode(200)
                .body("candidates.username", hasItems("zhang", "liu"))
                .body("minutes.classification", equalTo("SECRET"));

        // 负责人调整最终名单：只保留 zhang（预览后定向分发）
        given().headers(auth("approver")).contentType(ContentType.JSON)
                .body(Map.of("usernames", List.of("zhang")))
                .when().post("/api/minutes/" + minutesId + "/approve")
                .then().statusCode(200);

        List<Map<String, Object>> list = given().headers(auth("zhang"))
                .when().get("/api/minutes").then().statusCode(200)
                .extract().jsonPath().getList("$");
        Assertions.assertEquals("APPROVED", list.get(0).get("status"));
    }

    @Test
    @Order(4)
    void step04_authorizedEmployeeCanViewAndDownload() {
        Map<String, String> link = given().headers(auth("zhang"))
                .when().get("/api/view/" + minutesId + "/link")
                .then().statusCode(200).extract().jsonPath().getMap("");
        zhangToken = link.get("token");

        given().headers(auth("zhang"))
                .queryParam("t", zhangToken)
                .when().get("/api/view/" + minutesId)
                .then().statusCode(200)
                .body("body", equalTo(SENSITIVE_BODY))
                .body("versionNo", equalTo(1))
                .body("attachments", hasSize(1));

        firstAttachmentId = given().headers(auth("zhang")).queryParam("t", zhangToken)
                .when().get("/api/view/" + minutesId)
                .then().extract().jsonPath().getLong("attachments[0].id");

        given().headers(auth("zhang")).queryParam("t", zhangToken)
                .when().get("/api/attachments/" + firstAttachmentId + "/download")
                .then().statusCode(200)
                .body(is("v1 附件内容"));
    }

    @Test
    @Order(5)
    void step05_unauthorizedAndForgedAccessAreRejected() {
        // liu 未被列入最终名单：无链接且直接构造请求也被拒
        given().headers(auth("liu"))
                .when().get("/api/view/" + minutesId + "/link")
                .then().statusCode(403);

        given().headers(auth("liu")).queryParam("t", "forged.token.value")
                .when().get("/api/view/" + minutesId)
                .then().statusCode(403);

        // wang（2级，从无授权）伪造令牌访问下载：拒绝
        given().headers(auth("wang")).queryParam("t", "aaaa.aaaa.aaaa")
                .when().get("/api/attachments/" + firstAttachmentId + "/download")
                .then().statusCode(403);

        // zhang 的令牌对 liu 无效（链接绑定接收人，转发无效）
        given().headers(auth("liu")).queryParam("t", zhangToken)
                .when().get("/api/view/" + minutesId)
                .then().statusCode(403);
        given().headers(auth("liu")).queryParam("t", zhangToken)
                .when().get("/api/attachments/" + firstAttachmentId + "/download")
                .then().statusCode(403);

        // 无令牌直接访问附件也拒绝：不能绕过正文页直接读文件
        given().headers(auth("zhang"))
                .when().get("/api/attachments/" + firstAttachmentId + "/download")
                .then().statusCode(403);
    }

    @Test
    @Order(6)
    void step06_transferImmediatelyKillsOldLink() {
        // 张三从研发部高级工程师转岗到市场部
        given().headers(auth("admin")).contentType(ContentType.JSON)
                .body(Map.of("department", "市场部", "position", "品牌经理", "clearanceLevel", 2))
                .when().put("/api/employees/zhang/transfer")
                .then().statusCode(200);

        // 同一个旧链接：正文立即 403（转岗后授权行已作废，岗位校验也同步拦截）
        given().headers(auth("zhang")).queryParam("t", zhangToken)
                .when().get("/api/view/" + minutesId)
                .then().statusCode(403)
                .body("error", anyOf(containsString("授权名单"), containsString("岗位已变动")));

        // 附件下载同样立即 403，不能通过旧链接拿文件
        given().headers(auth("zhang")).queryParam("t", zhangToken)
                .when().get("/api/attachments/" + firstAttachmentId + "/download")
                .then().statusCode(403);

        // 历史分发记录仍保留
        given().headers(auth("approver"))
                .when().get("/api/monitor/minutes/" + minutesId + "/distributions")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(1))
                .body("[0].recipient", equalTo("张三"));
    }

    @Test
    @Order(7)
    void step07_republishAfterReturnAndRevokeKillsLink() throws Exception {
        // 张三调回研发部并恢复资质，修订 v2（新正文+新附件同一版本）
        given().headers(auth("admin")).contentType(ContentType.JSON)
                .body(Map.of("department", "研发部", "position", "高级工程师", "clearanceLevel", 3))
                .when().put("/api/employees/zhang/transfer")
                .then().statusCode(200);

        String v2Body = "【绝密正文】SECRET_PAYLOAD_2026 v2 修订";
        given().config(UTF8).headers(auth("recorder"))
                .multiPart(textPart("title", "收购标的评审纪要-修订"))
                .multiPart(textPart("classification", "SECRET"))
                .multiPart(textPart("body", v2Body))
                .multiPart("files", tmpFile("v2-附件", "v2 附件内容"), "application/octet-stream")
                .contentType("multipart/form-data")
                .when().put("/api/minutes/" + minutesId + "/revise")
                .then().statusCode(200)
                .body("draftVersionNo", equalTo(2))
                .body("currentVersionNo", equalTo(1)); // 新版本批准前 v1 仍为发布版本

        given().headers(auth("recorder")).contentType(ContentType.JSON)
                .body(Map.of("usernames", List.of("zhang")))
                .when().post("/api/minutes/" + minutesId + "/submit")
                .then().statusCode(200);
        given().headers(auth("approver")).contentType(ContentType.JSON)
                .body(Map.of("usernames", List.of("zhang")))
                .when().post("/api/minutes/" + minutesId + "/approve")
                .then().statusCode(200);

        String v2Token = given().headers(auth("zhang"))
                .when().get("/api/view/" + minutesId + "/link")
                .then().statusCode(200).extract().path("token");

        // 正文与附件都是 v2
        given().headers(auth("zhang")).queryParam("t", v2Token)
                .when().get("/api/view/" + minutesId)
                .then().statusCode(200)
                .body("versionNo", equalTo(2))
                .body("body", equalTo(v2Body))
                .body("attachments", hasSize(1));

        secondAttachmentId = given().headers(auth("zhang")).queryParam("t", v2Token)
                .when().get("/api/view/" + minutesId)
                .then().extract().jsonPath().getLong("attachments[0].id");
        Assertions.assertNotEquals(firstAttachmentId, secondAttachmentId);
        given().headers(auth("zhang")).queryParam("t", v2Token)
                .when().get("/api/attachments/" + secondAttachmentId + "/download")
                .then().statusCode(200).body(is("v2 附件内容"));

        // v1 的旧附件无法通过 v2 链接读取（正文与附件版本必须一致）
        given().headers(auth("zhang")).queryParam("t", v2Token)
                .when().get("/api/attachments/" + firstAttachmentId + "/download")
                .then().statusCode(403);

        // 负责人撤回张三的授权：旧链接（以及 v2 链接）立即失效
        given().headers(auth("approver"))
                .when().delete("/api/minutes/" + minutesId + "/recipients/zhang")
                .then().statusCode(200);

        given().headers(auth("zhang")).queryParam("t", v2Token)
                .when().get("/api/view/" + minutesId)
                .then().statusCode(403);
        given().headers(auth("zhang")).queryParam("t", v2Token)
                .when().get("/api/attachments/" + secondAttachmentId + "/download")
                .then().statusCode(403);
    }

    @Test
    @Order(8)
    void step08_auditAndEmailsRecordedWithoutBodyLeak() {
        // 审计包含拒绝事件
        given().headers(auth("approver"))
                .when().get("/api/monitor/audit")
                .then().statusCode(200)
                .body("findAll { it.allowed == false }.size()", greaterThan(0))
                .body("action", hasItem("DOWNLOAD_DENIED"));

        // 邮件只发提醒：全部邮件正文均不含敏感串，且包含密级标识与链接
        List<String> bodies = given().headers(auth("approver"))
                .when().get("/api/monitor/emails")
                .then().statusCode(200)
                .body("size()", greaterThan(0))
                .extract().jsonPath().getList("body");
        for (String body : bodies) {
            Assertions.assertFalse(body.contains(SENSITIVE_BODY), "提醒邮件不得包含敏感正文");
            Assertions.assertFalse(body.contains("v1 附件内容"), "提醒邮件不得包含附件内容");
            Assertions.assertTrue(body.contains("机密"), "提醒邮件应标注密级");
            Assertions.assertTrue(body.contains("/#/view/"), "提醒邮件应只包含访问链接");
        }

        // 员工无权查看审计/邮件
        given().headers(auth("zhang"))
                .when().get("/api/monitor/audit")
                .then().statusCode(403);
    }
}
