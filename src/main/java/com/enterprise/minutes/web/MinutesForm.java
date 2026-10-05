package com.enterprise.minutes.web;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

/**
 * 纪要创建/修订表单。正文(body)与附件(files)在同一个 multipart 请求中提交，
 * 服务端在同一事务内写入同一版本——API 层面就不存在"只更新一部分"的可能。
 */
public class MinutesForm {

    @RestForm("title")
    public String title;

    @RestForm("classification")
    public String classification;

    @RestForm("body")
    public String body;

    @RestForm("files")
    public List<FileUpload> files;
}
