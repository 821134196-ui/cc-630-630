package com.secmin.web;

import com.secmin.domain.*;
import com.secmin.service.AccessControlService;
import com.secmin.service.MinutesService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;

@Path("/api/minutes")
@Produces(MediaType.APPLICATION_JSON)
public class MinutesResource {

    @Inject
    EntityManager em;

    @Inject
    CurrentUser current;

    @Inject
    MinutesService minutesService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        AppUser u = current.require();
        if (u.getRole() != Role.SECRETARY && u.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅记录员可起草会议纪要");
        }
        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        if (title.isEmpty()) {
            throw new WebException(400, "标题不能为空");
        }
        Minutes m = minutesService.createMinutes(title, u.getId());
        return Map.of("id", m.getId(), "title", m.getTitle());
    }

    @GET
    public List<Map<String, Object>> list() {
        current.require();
        List<Minutes> all = em.createQuery("select m from Minutes m order by m.id desc", Minutes.class)
                .getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Minutes m : all) {
            Map<String, Object> dto = minutesSummary(m);
            out.add(dto);
        }
        return out;
    }

    @GET
    @Path("/{id}")
    public Map<String, Object> detail(@PathParam("id") Long id) {
        current.require();
        Minutes m = em.find(Minutes.class, id);
        if (m == null) {
            throw new WebException(404, "纪要不存在");
        }
        List<MinutesVersion> versions = em.createQuery(
                        "select v from MinutesVersion v where v.minutesId = :id order by v.versionNo desc",
                        MinutesVersion.class)
                .setParameter("id", id).getResultList();

        List<Map<String, Object>> vDtos = new ArrayList<>();
        for (MinutesVersion v : versions) {
            Map<String, Object> dto = versionDto(v);
            dto.put("attachments", em.createQuery(
                            "select a from Attachment a where a.versionId = :v order by a.id", Attachment.class)
                    .setParameter("v", v.getId()).getResultList().stream()
                    .map(this::attachmentDto).toList());
            dto.put("proposedRecipients", em.createQuery(
                            "select u from AppUser u, ProposedRecipient p "
                                    + "where p.versionId = :v and p.userId = u.id order by u.id", AppUser.class)
                    .setParameter("v", v.getId()).getResultList().stream()
                    .map(UserResource::userDto).toList());
            dto.put("reviewedByName", v.getReviewedById() == null ? null
                    : Optional.ofNullable(em.find(AppUser.class, v.getReviewedById()))
                            .map(AppUser::getName).orElse(null));
            vDtos.add(dto);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", m.getId());
        result.put("title", m.getTitle());
        result.put("createdAt", m.getCreatedAt().toString());
        result.put("currentVersionNo", m.getCurrentVersionNo());
        result.put("createdByName", em.find(AppUser.class, m.getCreatedById()).getName());
        result.put("versions", vDtos);
        return result;
    }

    /**
     * 记录员提交新修订版本：multipart 一次请求提交正文与全部附件（原子同版本）。
     */
    @POST
    @Path("/{id}/versions")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Map<String, Object> submitVersion(
            @PathParam("id") Long id,
            @RestForm("classification") String classificationStr,
            @RestForm("body") String body,
            @RestForm("changeNote") String changeNote,
            @RestForm("recipients") String recipientsJson,
            @RestForm("files") List<FileUpload> files) {

        AppUser u = current.require();
        if (u.getRole() != Role.SECRETARY && u.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅记录员可提交纪要版本");
        }
        Classification classification;
        try {
            classification = Classification.valueOf(classificationStr);
        } catch (Exception e) {
            throw new WebException(400, "密级取值非法: " + classificationStr);
        }

        List<MinutesService.SubmittedFile> submitted = new ArrayList<>();
        if (files != null) {
            for (FileUpload f : files) {
                try {
                    byte[] content = Files.readAllBytes(f.uploadedFile());
                    submitted.add(new MinutesService.SubmittedFile(
                            f.fileName(), f.contentType(), content));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        List<Long> recipientIds = parseLongArray(recipientsJson);
        MinutesVersion v = minutesService.submitVersion(id, classification, body, changeNote,
                submitted, recipientIds, u.getId());
        return versionDto(v);
    }

    /**
     * 负责人预览最终分发对象：返回建议名单 + 全部候选员工及其当前岗位密级/是否满足本版本密级。
     */
    @GET
    @Path("/versions/{vid}/preview")
    public Map<String, Object> preview(@PathParam("vid") Long vid) {
        AppUser u = current.require();
        if (u.getRole() != Role.APPROVER && u.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅负责人可预览分发对象");
        }
        MinutesVersion v = em.find(MinutesVersion.class, vid);
        if (v == null) {
            throw new WebException(404, "版本不存在");
        }
        Set<Long> proposed = new HashSet<>(em.createQuery(
                        "select p.userId from ProposedRecipient p where p.versionId = :v", Long.class)
                .setParameter("v", vid).getResultList());

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<AppUser> staff = em.createQuery(
                        "select u from AppUser u where u.enabled = true order by u.id", AppUser.class)
                .getResultList();
        for (AppUser s : staff) {
            if (s.getRole() == Role.SECRETARY && s.getRole() != Role.APPROVER) {
                // 记录员默认不作为接收对象，但负责人仍可显式加入——这里全部列出由负责人决定
            }
            Map<String, Object> dto = new LinkedHashMap<>(UserResource.userDto(s));
            dto.put("proposed", proposed.contains(s.getId()));
            dto.put("eligible", s.getClearanceLevel() >= v.getClassification().getLevel());
            candidates.add(dto);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", versionDto(v));
        result.put("proposedIds", proposed);
        result.put("candidates", candidates);
        return result;
    }

    /** 负责人审批，提交最终接收名单（必须全部满足密级、非空）。 */
    @POST
    @Path("/versions/{vid}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Map<String, Object> approve(@PathParam("vid") Long vid, Map<String, Object> body) {
        AppUser u = current.require();
        if (u.getRole() != Role.APPROVER && u.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅负责人可审批");
        }
        List<Long> ids = parseLongArray(body == null ? null : body.get("recipientIds"));
        MinutesVersion v = minutesService.approve(vid, ids, u.getId());
        return versionDto(v);
    }

    /** 负责人撤回审批：该版本全部旧链接立即失效。 */
    @POST
    @Path("/versions/{vid}/withdraw")
    @Transactional
    public Map<String, Object> withdraw(@PathParam("vid") Long vid) {
        AppUser u = current.require();
        if (u.getRole() != Role.APPROVER && u.getRole() != Role.ADMIN) {
            throw new WebException(403, "仅负责人可撤回审批");
        }
        MinutesVersion v = minutesService.withdraw(vid, u.getId());
        return versionDto(v);
    }

    // ---------- DTO ----------

    private Map<String, Object> minutesSummary(Minutes m) {
        MinutesVersion latest = em.createQuery(
                        "select v from MinutesVersion v where v.minutesId = :id "
                                + "order by v.versionNo desc", MinutesVersion.class)
                .setParameter("id", m.getId()).setMaxResults(1).getResultList().stream()
                .findFirst().orElse(null);
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", m.getId());
        dto.put("title", m.getTitle());
        dto.put("currentVersionNo", m.getCurrentVersionNo());
        dto.put("latestState", latest == null ? null : latest.getState().name());
        dto.put("latestStateLabel", latest == null ? null : latest.getState().getLabel());
        dto.put("classification", latest == null ? null : latest.getClassification().name());
        dto.put("classificationLabel", latest == null ? null
                : latest.getClassification().getLabel());
        dto.put("classificationLevel", latest == null ? null
                : latest.getClassification().getLevel());
        return dto;
    }

    private Map<String, Object> versionDto(MinutesVersion v) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", v.getId());
        dto.put("minutesId", v.getMinutesId());
        dto.put("versionNo", v.getVersionNo());
        dto.put("classification", v.getClassification().name());
        dto.put("classificationLabel", v.getClassification().getLabel());
        dto.put("classificationLevel", v.getClassification().getLevel());
        dto.put("bodyText", v.getBodyText());
        dto.put("changeNote", v.getChangeNote());
        dto.put("state", v.getState().name());
        dto.put("stateLabel", v.getState().getLabel());
        dto.put("createdAt", v.getCreatedAt().toString());
        dto.put("reviewedAt", v.getReviewedAt() == null ? null : v.getReviewedAt().toString());
        dto.put("approvedRecipientIds",
                v.getApprovedRecipientIds() == null ? List.of()
                        : AccessControlService.parseIdList(v.getApprovedRecipientIds()).stream()
                                .sorted().toList());
        return dto;
    }

    private Map<String, Object> attachmentDto(Attachment a) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", a.getId());
        dto.put("versionId", a.getVersionId());
        dto.put("fileName", a.getFileName());
        dto.put("contentType", a.getContentType());
        dto.put("sizeBytes", a.getSizeBytes());
        return dto;
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseLongArray(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        json = json.trim();
        if (json.startsWith("[")) {
            // 简单 JSON 数组解析，避免引入额外依赖
            String inner = json.substring(1, json.length() - 1);
            List<Long> ids = new ArrayList<>();
            for (String s : inner.split(",")) {
                String t = s.trim().replace("\"", "");
                if (!t.isEmpty()) {
                    ids.add(Long.parseLong(t));
                }
            }
            return ids;
        }
        throw new WebException(400, "接收人名单格式应为 JSON 数组");
    }

    @SuppressWarnings("unchecked")
    private List<Long> parseLongArray(Object obj) {
        if (obj == null) {
            return new ArrayList<>();
        }
        if (obj instanceof List<?> list) {
            List<Long> ids = new ArrayList<>();
            for (Object o : list) {
                ids.add(Long.parseLong(String.valueOf(o)));
            }
            return ids;
        }
        return parseLongArray(String.valueOf(obj));
    }
}
