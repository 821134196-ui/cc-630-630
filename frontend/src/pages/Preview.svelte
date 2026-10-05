<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { go } from '../stores.js';

  export let versionId;

  let data = null;
  let error = '';
  let msg = '';
  let picked = {};
  let busy = false;

  onMount(load);

  async function load() {
    error = '';
    try {
      data = await api.get(`/api/minutes/versions/${versionId}/preview`);
      const preset = new Set(
        data.version.approvedRecipientIds.length
          ? data.version.approvedRecipientIds
          : [...data.proposedIds]
      );
      data.candidates.forEach(c => picked[c.id] = preset.has(c.id));
    } catch (e) { error = e.message; }
  }

  $: v = data?.version;
  $: selectedIds = data ? data.candidates.filter(c => picked[c.id]).map(c => c.id) : [];
  $: selected = data ? data.candidates.filter(c => picked[c.id]) : [];
  $: blocked = data ? selected.filter(c => !c.eligible) : [];

  async function approve() {
    error = ''; msg = '';
    if (selectedIds.length === 0) { error = '最终分发名单不能为空'; return; }
    if (blocked.length > 0) {
      error = '名单中存在岗位密级低于纪要密级的人员，无法审批：'
        + blocked.map(u=>u.name).join('、');
      return;
    }
    if (!confirm(`确认审批通过？最终分发对象共 ${selectedIds.length} 人：\n`
      + selected.map(u=>`${u.name}(${u.clearanceLabel})`).join('、')
      + '\n\n审批后将向其发送提醒邮件（不含正文），旧版本链接立即失效。')) return;
    busy = true;
    try {
      await api.post(`/api/minutes/versions/${versionId}/approve`, { recipientIds: selectedIds });
      msg = '审批通过，已按最终名单定向分发。';
      await load();
    } catch (e) { error = e.message; }
    busy = false;
  }

  async function withdraw() {
    error = ''; msg = '';
    if (!confirm('确认撤回该版本审批？撤回后：\n· 全部接收人的旧链接立即失效；\n· 历史分发记录与审计保留；\n· 系统向接收人发送失效通知（不含正文）。')) return;
    busy = true;
    try {
      await api.post(`/api/minutes/versions/${versionId}/withdraw`, {});
      msg = '已撤回审批，所有旧链接已立即失效。';
      await load();
    } catch (e) { error = e.message; }
    busy = false;
  }
</script>

{#if !v}
  {#if error}<div class="card error">{error}</div>{:else}<div class="card muted">加载中…</div>{/if}
{:else}
  <div class="classification-banner {v.classification}">
    <div>
      <div style="font-size:12px;opacity:.85">审批密级 / CLASSIFICATION</div>
      <div class="big">★ {v.classificationLabel}（{v.classification}）★</div>
    </div>
    <div style="flex:1"></div>
    <div class="hint">版本 v{v.versionNo} · {v.changeNote || ''}<br/>仅岗位密级不低于该密级的人员可进入最终名单。</div>
  </div>

  <div class="card">
    <div class="row">
      <h2 style="margin:0;flex:1">最终分发对象预览与审批</h2>
      <span class="state {v.state}">{v.stateLabel}</span>
    </div>
    <div class="sub" style="margin-top:8px">
      记录员建议名单 {data.proposedIds.size} 人；请核对后形成<strong>最终审批名单</strong>。
      审批通过瞬间冻结名单快照，之后员工转岗或被撤销授权都不改变快照，只影响实时访问核验结果。
    </div>
    {#if error}<div class="error">{error}</div>{/if}
    {#if msg}<div class="ok-msg">{msg}</div>{/if}

    <!-- 最终分发对象实时预览 -->
    <div style="background:#f8fafc;border:1px solid var(--line);border-radius:8px;padding:12px 14px;margin:12px 0">
      <strong>最终将分发给 {selectedIds.length} 人：</strong>
      {#if selectedIds.length === 0}<span class="muted">（尚未选择）</span>{/if}
      <div style="margin-top:6px">
        {#each selected as u}
          <span class="chip" class:chip-danger={!u.eligible} style={u.eligible?'':'background:var(--danger-soft);color:var(--danger)'}>
            {u.name} · {u.clearanceLabel}{u.proposed ? ' · 建议' : ''}{u.eligible ? '' : ' · 密级不足!'}
          </span>
        {/each}
      </div>
    </div>

    <label>勾选最终接收人（可增删建议名单）</label>
    {#each data.candidates as c}
      <div class="person-pick" class:ineligible={!c.eligible}>
        <input type="checkbox" bind:checked={picked[c.id]} />
        <div class="names">
          <strong>{c.name}</strong>（{c.account}）· {c.roleLabel} · {c.post}
          {#if c.proposed}<span class="chip" style="background:var(--brand-soft);color:var(--brand)">记录员建议</span>{/if}
        </div>
        <span class="muted">岗位密级 {c.clearanceLabel}</span>
        {#if c.eligible}<span style="color:var(--ok);font-size:12px">✓ 满足</span>
        {:else}<span style="color:var(--danger);font-size:12px">✗ 密级不足</span>{/if}
      </div>
    {/each}

    <div class="row" style="margin-top:16px">
      {#if v.state === 'PENDING'}
        <button on:click={approve} disabled={busy}>确认名单并审批分发（{selectedIds.length} 人）</button>
      {:else if v.state === 'APPROVED'}
        <button class="danger" on:click={withdraw} disabled={busy}>撤回审批（旧链接全部立即失效）</button>
      {:else}
        <span class="muted">该版本已{v.stateLabel}，不可再审批。</span>
      {/if}
      <button class="secondary" on:click={()=>go('approvals')}>返回审批中心</button>
    </div>
  </div>

  <div class="card">
    <h2>本版正文与附件（同版本发布）</h2>
    <div class="sub">邮件不会包含以下任何内容，接收人必须点击专属链接并通过实时核验后才能看到。</div>
    <pre class="body">{v.bodyText}</pre>
    <div class="muted" style="margin-top:8px">审批通过后由系统按最终名单定向投递。</div>
  </div>
{/if}
