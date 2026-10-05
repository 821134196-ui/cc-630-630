package com.enterprise.minutes.web;

import com.enterprise.minutes.domain.*;
import com.enterprise.minutes.security.CurrentUser;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDateTime;
import java.util.List;

/** 审计日志、模拟邮件外发箱、历史分发记录查询（审批人/管理员/记录员）。 */
@Path("/api/monitor")
@Produces(MediaType.APPLICATION_JSON)
public class MonitorResource {

    @Inject
    CurrentUser currentUser;

    public record AuditDto(Long id, String username, Long minutesId, String minutesTitle,
                           Long attachmentId, String action, boolean allowed,
                           String reason, LocalDateTime at) {
    }

    public record EmailDto(Long id, String toEmail, String subject, String body, LocalDateTime sentAt) {
    }

    public record DistributionDto(Long id, Long minutesId, String title, int versionNo,
                                  String recipient, String email, LocalDateTime sentAt) {
    }

    @GET
    @jakarta.transaction.Transactional
    @Path("/audit")
    public List<AuditDto> audit() {
        Employee me = currentUser.require();
        if (me.role == Role.STAFF) {
            throw new jakarta.ws.rs.ForbiddenException("无权查看审计日志");
        }
        return AuditLog.<AuditLog>listAll(io.quarkus.panache.common.Sort.by("at", io.quarkus.panache.common.Sort.Direction.Descending))
                .stream().map(l -> new AuditDto(l.id, l.username,
                        l.minutes != null ? l.minutes.id : null,
                        l.minutes != null ? l.minutes.title : null,
                        l.attachment != null ? l.attachment.id : null,
                        l.action.name(), l.allowed, l.reason, l.at))
                .toList();
    }

    @GET
    @jakarta.transaction.Transactional
    @Path("/emails")
    public List<EmailDto> emails() {
        Employee me = currentUser.require();
        if (me.role == Role.STAFF) {
            throw new jakarta.ws.rs.ForbiddenException("无权查看邮件外发箱");
        }
        return EmailLog.<EmailLog>listAll(io.quarkus.panache.common.Sort.by("sentAt", io.quarkus.panache.common.Sort.Direction.Descending))
                .stream().map(m -> new EmailDto(m.id, m.toEmail, m.subject, m.body, m.sentAt))
                .toList();
    }

    @GET
    @jakarta.transaction.Transactional
    @Path("/minutes/{id}/distributions")
    public List<DistributionDto> distributions(@PathParam("id") Long id) {
        Employee me = currentUser.require();
        if (me.role == Role.STAFF) {
            throw new jakarta.ws.rs.ForbiddenException("无权查看分发记录");
        }
        Minutes m = Minutes.findById(id);
        if (m == null) {
            throw new jakarta.ws.rs.NotFoundException("纪要不存在");
        }
        return DistributionRecord.<DistributionRecord>list("minutes",
                        io.quarkus.panache.common.Sort.by("sentAt",
                                io.quarkus.panache.common.Sort.Direction.Descending), m)
                .stream().map(d -> new DistributionDto(d.id, d.minutes.id, d.minutes.title,
                        d.versionNo, d.recipient.name, d.recipientEmail, d.sentAt))
                .toList();
    }
}
