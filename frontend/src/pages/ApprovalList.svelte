<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { go } from '../stores.js';

  let rows = [];
  let loading = true;

  onMount(load);

  async function load() {
    loading = true;
    const minutes = await api.get('/api/minutes');
    const details = await Promise.all(minutes.map(m => api.get('/api/minutes/' + m.id)));
    rows = [];
    for (const d of details) {
      for (const v of d.versions) {
        rows.push({
          minutesId: d.id, title: d.title, version: v
        });
      }
    }
    loading = false;
  }

  $: pending = rows.filter(r => r.version.state === 'PENDING');
  $: active = rows.filter(r => r.version.state === 'APPROVED');
  $: closed = rows.filter(r => ['WITHDRAWN', 'SUPERSEDED'].includes(r.version.state));
</script>

<div class="card">
  <h2>审批中心</h2>
  <div class="sub">请预览并确认<strong>最终分发对象</strong>：可在建议名单基础上增删，但接收人岗位密级必须不低于纪要密级。
    通过后系统按最终名单定向发送提醒邮件（邮件不含正文）。</div>
  {#if loading}<div class="muted">加载中…</div>{/if}

  <h3 style="margin:18px 0 8px">待审批（{pending.length}）</h3>
  {#if pending.length === 0}<div class="muted">暂无待审批版本。</div>{/if}
  {#each pending as r}
    <div class="v versions">
      <div class="v" style="border:1px solid var(--line);border-radius:8px;padding:12px 14px">
        <div class="head" style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
          <strong>{r.title}</strong> v{r.version.versionNo}
          <span class="badge {r.version.classification}">{r.version.classificationLabel}</span>
          <span class="state PENDING">{r.version.stateLabel}</span>
          <span style="flex:1"></span>
          <button on:click={()=>go('preview/' + r.version.id)}>预览最终分发对象并审批</button>
        </div>
        <div class="muted" style="margin-top:6px">修订说明：{r.version.changeNote || '—'} · 建议接收人 {r.version.proposedRecipients.length} 人</div>
      </div>
    </div>
  {/each}

  <h3 style="margin:22px 0 8px">已审批 / 可撤回（{active.length}）</h3>
  {#if active.length === 0}<div class="muted">暂无生效版本。</div>{/if}
  {#each active as r}
    <div style="border:1px solid var(--line);border-radius:8px;padding:12px 14px;margin-bottom:10px">
      <div style="display:flex;gap:10px;align-items:center;flex-wrap:wrap">
        <strong>{r.title}</strong> v{r.version.versionNo}
        <span class="badge {r.version.classification}">{r.version.classificationLabel}</span>
        <span class="state APPROVED">{r.version.stateLabel}</span>
        <span style="flex:1"></span>
        <button class="ghost" on:click={()=>go('preview/' + r.version.id)}>查看分发对象</button>
      </div>
    </div>
  {/each}

  <h3 style="margin:22px 0 8px">已失效（{closed.length}）</h3>
  {#each closed as r}
    <div class="muted" style="padding:4px 0">
      {r.title} v{r.version.versionNo}
      <span class="badge {r.version.classification}" style="margin:0 6px">{r.version.classificationLabel}</span>
      <span class="state {r.version.state}">{r.version.stateLabel}</span>
      <span style="margin-left:8px">历史分发记录与审计仍保留</span>
    </div>
  {/each}
</div>
