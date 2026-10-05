package com.secmin.service;

import com.secmin.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * 模拟邮件服务：不落 SMTP，邮件内容写库并在本地 mail 目录留一份 .txt。
 * 安全红线：正文与附件内容绝不出现在邮件中，只有提醒语 + 密级标识 + 一次性访问链接。
 */
@ApplicationScoped
public class MailService {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "app.mail-dir")
    String mailDir;

    @ConfigProperty(name = "app.base-url")
    String baseUrl;

    /**
     * 发送“纪要已分发”提醒邮件。
     */
    public SimulatedMail sendDistributionMail(AppUser to, Minutes minutes, MinutesVersion version,
                                              DistributionGrant grant) {
        String link = baseUrl + "/#/view/" + grant.getToken();
        String subject = "【密级提醒·" + version.getClassification().getLabel() + "】会议纪要《"
                + minutes.getTitle() + "》v" + version.getVersionNo() + " 已定向分发给您";
        String body = """
                %s 您好：

                您有一份新的会议纪要定向分发提醒（仅通知，不含任何正文内容）。
                  标题：%s
                  版本：v%d
                  密级：%s（请按密级要求处理，不得转发链接或扩散）
                  专属访问链接：%s

                注意：
                1. 本链接为您本人专属，每次打开都将按您当前岗位密级与授权状态实时核验；
                2. 转岗、授权撤销或负责人撤回审批后，链接立即失效；
                3. 收到本邮件不代表持续访问授权，一切以访问时核验结果为准。

                （此为系统模拟邮件，由会议纪要保密分发系统发出）
                """.formatted(to.getName(), minutes.getTitle(), version.getVersionNo(),
                version.getClassification().getLabel(), link);
        return persist(to, minutes.getId(), version.getId(),
                version.getClassification().name(), subject, body);
    }

    /**
     * 发送“访问权失效”通知（撤回审批 / 撤销授权 / 转岗后不再满足密级）。依然不含正文。
     */
    public SimulatedMail sendAccessInvalidatedMail(AppUser to, Minutes minutes, String reason) {
        String subject = "【权限变更通知】《" + minutes.getTitle() + "》您的访问链接已失效";
        String body = """
                %s 您好：

                会议纪要《%s》对您的访问授权已终止。
                原因：%s
                历史分发记录依法保留，但您手中的旧链接现已无法访问正文与附件。

                （此为系统模拟邮件，不含任何纪要内容）
                """.formatted(to.getName(), minutes.getTitle(), reason);
        return persist(to, minutes.getId(), null, null, subject, body);
    }

    private SimulatedMail persist(AppUser to, Long minutesId, Long versionId,
                                  String classification, String subject, String body) {
        SimulatedMail mail = new SimulatedMail();
        mail.setSentAt(Instant.now());
        mail.setToAccount(to.getAccount());
        mail.setToEmail(to.getEmail());
        mail.setSubject(subject);
        mail.setBodyText(body);
        mail.setMinutesId(minutesId);
        mail.setVersionId(versionId);
        mail.setClassification(classification);
        em.persist(mail);
        em.flush();

        try {
            Path dir = Paths.get(mailDir);
            Files.createDirectories(dir);
            String stamp = Long.toString(System.currentTimeMillis());
            Path file = dir.resolve(stamp + "_" + to.getAccount() + "_mail" + mail.getId() + ".txt");
            Files.writeString(file,
                    "To: " + to.getEmail() + "\nSubject: " + subject + "\n\n" + body,
                    StandardOpenOption.CREATE_NEW);
            mail.setFilePath(file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return mail;
    }
}
