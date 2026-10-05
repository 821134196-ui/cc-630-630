<script>
  import { onMount } from 'svelte';
  import { api, authedDownload } from '../lib/api.js';
  import { classificationOf } from '../lib/constants.js';
  import Badge from '../lib/Badge.svelte';

  export let id;
  export let token;
  export let go;

  let doc = null;
  let denied = '';
  let loading = true;
  let downloadError = '';

  $: cls = doc ? classificationOf(doc.classification) : null;

  onMount(async () => {
    try {
      doc = await api.get(`/api/view/${id}?t=${encodeURIComponent(token)}`);
    } catch (e) {
      denied = e.message;
    } finally {
      loading = false;
    }
  });

  async function download(a) {
    downloadError = '';
    const url = `/api/attachments/${a.id}/download?t=${encodeURIComponent(token)}`;
    try {
      await authedDownload(url, a.filename);
    } catch (e) {
      downloadError = e.message;
    }
  }
</script>

{#if loading}
  <p class="muted">正在按您当前的岗位与授权实时核验链接…</p>
{:else if denied}
  <div class="panel">
    <div class="deny-box">
      <div style="font-size:18px">⛔ 访问被拒绝</div>
      <div style="margin-top:10px;font-weight:400">{denied}</div>
    </div>
    <p class="muted" style="margin-top:16px">
      提醒邮件只是通知，不代表持续访问权。若您已转岗、岗位密级资质变化或授权被负责人撤回，
      旧链接会立即失效；尝试访问的行为已记入审计日志。
    </p>
    <button on:click={() => go('#/')}>返回我的纪要</button>
  </div>
{:else if doc && cls}
  <div class="panel">
    <div class="class-banner" style={`border-color:${cls.color};color:${cls.color};background:${cls.bg}`}>
      <span class="big">★ {cls.label}</span>
      <span>您正在查阅 {cls.level} 级保密资料，请勿转发链接或内容</span>
    </div>
    <h1>{doc.title}</h1>
    <p class="muted">发布版本 v{doc.versionNo} · 发布时间 {doc.publishedAt?.replace('T', ' ').slice(0, 16)}</p>
  </div>

  <div class="panel">
    <h2 style="margin-top:0">纪要正文</h2>
    <div style="white-space:pre-wrap;line-height:1.8">{doc.body}</div>
  </div>

  <div class="panel">
    <h2 style="margin-top:0">附件（{doc.attachments.length}）</h2>
    <p class="muted">下载附件时会再次校验您的当前权限，无法绕过本页面直接读取文件。</p>
    {#if downloadError}<div class="error">{downloadError}</div>{/if}
    <table>
      {#each doc.attachments as a}
        <tr>
          <td>{a.filename}</td>
          <td class="muted">{a.contentType} · {(a.size / 1024).toFixed(1)} KB · v{a.versionNo}</td>
          <td><button class="primary" on:click={() => download(a)}>下载（实时鉴权）</button></td>
        </tr>
      {/each}
    </table>
  </div>

  <p class="muted">本页正文与附件同属 v{doc.versionNo}，如纪要被修订并重新发布，您再打开链接将看到最新版本。</p>
  <button on:click={() => go('#/')}>返回我的纪要</button>
{/if}
