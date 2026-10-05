package com.enterprise.minutes.service;

import com.enterprise.minutes.domain.EmailLog;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * 模拟邮件接口：不真正发信，把邮件内容写入 email_log。
 * 安全要求：提醒邮件只含标题/密级/链接，【绝不】附带敏感正文或附件。
 */
@ApplicationScoped
public class MailService {

    /**
     * @return 落库的邮件日志（用于关联分发记录）
     */
    public EmailLog sendNotification(String toEmail, String recipientName,
                                     String minutesTitle, ClassificationInfo cls,
                                     String viewLink, int versionNo) {
        String body = """
                %s 您好：

                您有一份新的会议纪要提醒可供查阅（版本 v%d）。

                  标题：%s
                  密级：【%s】

                请通过以下内网链接查阅，访问时将按您当前的岗位与授权实时核验：
                %s

                请注意：
                1. 本邮件仅为提醒，不含纪要正文与附件；
                2. 如您已转岗或授权被撤回，该链接将立即失效；
                3. 请勿转发链接，转发的链接对他人无效。
                """.formatted(recipientName, versionNo, minutesTitle, cls.label(), viewLink);

        EmailLog log = new EmailLog();
        log.toEmail = toEmail;
        log.subject = "【%s】会议纪要提醒：%s（v%d）".formatted(cls.label(), minutesTitle, versionNo);
        log.body = body;
        log.persist();
        return log;
    }

    public record ClassificationInfo(String label) {
        public static ClassificationInfo of(com.enterprise.minutes.domain.Classification c) {
            return new ClassificationInfo(c.getLabel());
        }
    }
}
