<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { users } from '../stores.js';
  import { go } from '../stores.js';

  export let minutesId;

  const LEVELS = [
    { v: 'PUBLIC', label: '公开', level: 1, warn: '可向公司内外公开的信息' },
    { v: 'INTERNAL', label: '内部', level: 2, warn: '仅限公司内部员工知悉' },
    { v: 'CONFIDENTIAL', label: '秘密', level: 3, warn: '知悉范围受限，泄露将造成较大损失' },
    { v: 'SECRET', label: '机密', level: 4, warn: '核心敏感信息，泄露将造成重大损失' }
  ];

  let detail = null;
  let error = '';
  let msg = '';
  let submitting = false;

  // 新修订表单
  let classification = 'CONFIDENTIAL';
  let body = '';
  let changeNote = '';
  let files = [];
  let chosen = {}; // userId -> bool

  onMount(load);

  async function load() {
    error = '';
    try {
      detail = await api.get('/api/minutes/' + minutesId);
      const latest = detail.versions[0];
      if (latest) {
        // 默认沿用上一版密级与正文，便于修订
        classification = latest.classification;
        body = latest.bodyText;
        const ids = latest.approvedRecipientIds;
        const preset = new Set(ids.length ? ids : latest.proposedRecipients.map(u=>u.id));
        $users.forEach(u => chosen[u.id] = preset.has(u.id));
      } else {
        $users.forEach(u => chosen[u.id] = false);
      }
    } catch (e) { error = e.message; }
  }

  $: curLevel = LEVELS.find(l => l.v === classification);
  $: eligibleIds = new Set($users.filter(u => u.clearanceLevel >= (curLevel?.level ?? 0)).map(u => u.id));

  function onFiles(e) {
    files = Array.from(e.target.files || []);
  }

  async function submit() {
    error = ''; msg = '';
    if (!body.trim()) { error = '正文不能为空'; return; }
    const recipientIds = Object.keys(chosen).filter(k => chosen[k] && eligibleIds.has(+k)).map(Number);
    if (recipientIds.length === 0) { error = '请至少选择一位岗位密级满足本密级的建议接收人'; return; }

    submitting = true;
    try {
      const fd = new FormData();
      fd.append('classification', classification);
      fd.append('body', body);
      fd.append('changeNote', changeNote || `修订 v${(detail?.currentVersionNo ?? 0) + 1}`);
      fd.append('recipients', JSON.stringify(recipientIds));
      files.forEach(f => fd.append('files', f));
      const v = await api.postForm(`/api/minutes/${minutesId}/versions`, fd);
      msg = `版本 v${v.versionNo} 已提交，正文与 ${files.length} 个附件作为同一版本进入待审批。`;
      files = []; changeNote = '';
      await load();
    } catch (e) { error = e.message; }
    submitting = false;
  }
</script>

{#if !detail}
  {#if error}<div class="card error">{error}</div>{:else}<div class="card muted">加载中…</div>{/if}
{:else}
  <!-- 显著密级显示（随当前选择的密级变色） -->
  <div class="classification-banner {classification}">
    <div>
      <div style="font-size:12px;opacity:.85">当前编辑密级 / CLASSIFICATION</div>
      <div class="big">★ {curLevel.label}（{classification}）★</div>
    </div>
    <div style="flex:1"></div>
    <div class="hint">{curLevel.warn}<br/>密级决定可接收人范围；修订时如提升密级，原低密级接收人将不再可加入。</div>
  </div>

  <div class="card">
    <h2>{detail.title}</h2>
    <div class="sub">记录员 {detail.createdByName} · 当前版本 v{detail.currentVersionNo}</div>

    {#if error}<div class="error">{error}</div>{/if}
    {#if msg}<div class="ok-msg">{msg}</div>{/if}

    <div class="grid2">
      <div>
        <label>本修订密级（显著标注）</label>
        <select bind:value={classification}>
          {#each LEVELS as l}
            <option value={l.v}>{l.label}（{l.v}）— {l.warn}</option>
          {/each}
        </select>
      </div>
      <div>
        <label>修订说明</label>
        <input type="text" placeholder="本次修订内容说明" bind:value={changeNote} />
      </div>
    </div>

    <label>纪要正文（敏感内容仅存系统，绝不进入邮件）</label>
    <textarea bind:value={body} placeholder="在此录入/修订正文…"></textarea>

    <label>附件（与正文在同一次提交中成为同一版本；修订需连同正文一起重新发布）</label>
    <input type="file" multiple on:change={onFiles} />
    {#if files.length > 0}
      <div class="muted" style="margin-top:6px">本次将一起发布 {files.length} 个附件：
        {#each files as f}<span class="chip">{f.name}（{(f.size/1024).toFixed(1)}KB）</span>{/each}
      </div>
    {/if}

    <label style="margin-top:16px">建议接收名单（提交给负责人审批，可被增删）</label>
    <div class="muted" style="margin-bottom:6px">
      密级要求：岗位密级 ≥ {curLevel.label}；灰色人员当前密级不足，无法选择。
    </div>
    {#each $users as u}
      <div class="person-pick" class:ineligible={!eligibleIds.has(u.id)}>
        <input type="checkbox" disabled={!eligibleIds.has(u.id)} bind:checked={chosen[u.id]} />
        <div class="names">
          <strong>{u.name}</strong>（{u.account}）· {u.roleLabel} · {u.post}
        </div>
        <span class="badge {u.clearanceLevel >= 3 ? 'SECRET' : 'INTERNAL'}"
              style={u.clearanceLevel===3?'background:#fef3c7;color:#b45309':''}>
          岗位密级 {u.clearanceLabel}
        </span>
      </div>
    {/each}

    <div class="row" style="margin-top:16px">
      <button on:click={submit} disabled={submitting}>
        {submitting ? '提交中…' : `提交修订版本（正文 + ${files.length} 附件同一版本）`}
      </button>
      <button class="secondary" on:click={()=>go('minutes')}>返回列表</button>
    </div>
  </div>

  <div class="card versions">
    <h2>版本历史（正文与附件始终同版本）</h2>
    {#each detail.versions as v}
      <div class="v">
        <div class="head">
          <strong>v{v.versionNo}</strong>
          <span class="badge {v.classification}">{v.classificationLabel}</span>
          <span class="state {v.state}">{v.stateLabel}</span>
          <span class="sp"></span>
          <span class="muted">{v.createdAt?.slice(0,19).replace('T',' ')}</span>
        </div>
        <div class="muted" style="margin:6px 0">修订说明：{v.changeNote || '—'}</div>
        <div>附件：
          {#if v.attachments.length === 0}<span class="muted">无</span>{/if}
          {#each v.attachments as a}<span class="chip">📎 {a.fileName}（{(a.sizeBytes/1024).toFixed(1)}KB）</span>{/each}
        </div>
        <div style="margin-top:4px">审批接收人：
          {#if v.approvedRecipientIds.length}
            {#each v.proposedRecipients.filter(u=>v.approvedRecipientIds.includes(u.id)) as u}
              <span class="chip">{u.name}</span>
            {/each}
          {:else}
            {#each v.proposedRecipients as u}<span class="chip">{u.name}（建议）</span>{/each}
          {/if}
        </div>
      </div>
    {/each}
  </div>
{/if}
