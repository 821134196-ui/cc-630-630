<script>
  import { api, authedDownload } from '../api.js';
  import { go } from '../stores.js';

  export let token;

  let doc = null;
  let denied = null;
  let loading = true;
  let dlError = '';

  $: loadDoc(token);
  async function loadDoc(t) {
    loading = true; doc = null; denied = null; dlError = '';
    try {
      doc = await api.get('/api/access/view/' + t);
    } catch (e) {
      if (e.status === 403) denied = e.message;
      else denied = e.message;
    }
    loading = false;
  }

  async function download(a) {
    dlError = '';
    try {
      await authedDownload(`/api/access/attachment/${token}/${a.id}`, a.fileName);
    } catch (e) { dlError = `${a.fileName}：${e.message}`; }
  }
</script>

{#if loading}
  <div class="card muted">正在实时核验您的当前岗位、密级与授权状态…</div>
{:else if denied}
  <div class="card">
    <div class="denied-box">
      <div class="big">⛔</div>
      <h2 style="color:var(--danger)">访问被拒绝</h2>
      <p style="font-size:15px">{denied}</p>
      <p class="muted" style="margin-top:14px">
        本次拒绝已写入访问审计。历史分发记录仍然保留，但您无法通过此链接查看正文或下载附件。
        如确有业务需要，请联系纪要负责人重新走审批分发。
      </p>
      <button class="secondary" on:click={()=>go('links')}>返回我的纪要</button>
    </div>
  </div>
{:else if doc}
  <div class="classification-banner {doc.classification}">
    <div>
      <div style="font-size:12px;opacity:.85">文件密级 / CLASSIFICATION</div>
      <div class="big">★ {doc.classificationLabel}（{doc.classification}）★</div>
    </div>
    <div style="flex:1"></div>
    <div class="hint">
      本页面为 {doc.viewer.name} 的专属访问页面，请勿转发链接。<br/>
      转岗、授权撤销或审批撤回后，本链接立即失效。
    </div>
  </div>

  <div class="card">
    <div class="row">
      <h2 style="margin:0;flex:1">{doc.minutesTitle}</h2>
      <span class="muted">版本 v{doc.versionNo}</span>
    </div>
    <div class="sub" style="margin-top:6px">审批通过时间：{doc.reviewedAt?.slice(0,19).replace('T',' ')} · 每次打开均已实时核验</div>
    {#if doc.changeNote}<div class="muted">修订说明：{doc.changeNote}</div>{/if}

    <pre class="body">{doc.bodyText}</pre>

    <h3 style="margin:20px 0 8px">附件（{doc.attachments.length}）</h3>
    {#if dlError}<div class="error">{dlError}</div>{/if}
    <div class="att-list">
      {#if doc.attachments.length === 0}<div class="muted">本版本无附件。</div>{/if}
      {#each doc.attachments as a}
        <div class="att">
          <span style="font-size:20px">📎</span>
          <div class="fname">{a.fileName}</div>
          <span class="muted">{(a.sizeBytes/1024).toFixed(1)} KB · {a.contentType}</span>
          <button on:click={()=>download(a)}>下载（实时鉴权）</button>
        </div>
      {/each}
    </div>

    <div class="watermark">
      {doc.viewer.name} · {doc.viewer.account} · {doc.viewer.post} · 专属页面 · 下载与查看全程审计
    </div>
  </div>

  <div class="card">
    <button class="secondary" on:click={()=>go('links')}>返回我的纪要</button>
  </div>
{/if}
