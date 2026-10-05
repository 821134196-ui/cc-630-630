<script>
  import { onMount } from 'svelte';
  import { api, authedDownload } from '../lib/api.js';
  import { classificationOf, STATUS_LABELS } from '../lib/constants.js';
  import Badge from '../lib/Badge.svelte';

  export let id;
  export let go;

  let minutes = null;
  let candidates = [];
  let grants = [];
  let draft = null;
  let distributions = [];
  let employees = [];
  let addUsername = '';
  let finalSet = new Set();
  let rejectComment = '';
  let showReject = false;
  let error = '';
  let msg = '';
  let busy = false;

  $: cls = minutes ? classificationOf(minutes.classification) : null;

  async function load() {
    error = '';
    try {
      minutes = await api.get(`/api/minutes/${id}`);
      try {
        const p = await api.get(`/api/minutes/${id}/approval-preview`);
        candidates = p.candidates;
        grants = p.currentGrants;
        finalSet = new Set(p.candidates.filter((c) => c.eligible).map((c) => c.username));
        finalSet = finalSet;
      } catch (_) {
        // 非待审批状态：展示已有授权与分发历史
        grants = await api.get(`/api/minutes/${id}/grants`);
        finalSet = new Set(grants.filter((g) => g.active).map((g) => g.username));
      }
      try {
        draft = await api.get(`/api/minutes/${id}/draft-content`);
      } catch (_) {
        draft = null;
      }
      try {
        employees = await api.get('/api/employees');
      } catch (_) {
        employees = [];
      }
      try {
        distributions = await api.get(`/api/monitor/minutes/${id}/distributions`);
      } catch (_) {
        distributions = [];
      }
    } catch (e) {
      error = e.message;
    }
  }

  onMount(load);

  function toggle(u) {
    if (finalSet.has(u)) finalSet.delete(u);
    else finalSet.add(u);
    finalSet = new Set(finalSet);
  }

  async function approve() {
    error = '';
    if (finalSet.size === 0) {
      error = '最终接收名单不能为空';
      return;
    }
    if (!confirm(`确认发布并向 ${finalSet.size} 名员工定向发送提醒邮件？\n（邮件仅含链接，不含正文与附件）`)) return;
    busy = true;
    try {
      await api.post(`/api/minutes/${id}/approve`, { usernames: [...finalSet] });
      msg = '已批准发布，提醒邮件已进入模拟外发箱，分发记录已留存。';
      setTimeout(() => load(), 600);
    } catch (e) {
      error = e.message;
    } finally {
      busy = false;
    }
  }

  async function reject() {
    busy = true;
    error = '';
    try {
      await api.post(`/api/minutes/${id}/reject`, { comment: rejectComment });
      msg = '已驳回。';
      setTimeout(() => go('#/'), 600);
    } catch (e) {
      error = e.message;
    } finally {
      busy = false;
    }
  }

  async function revoke(username) {
    if (!confirm(`撤回 ${username} 的授权？其旧链接将【立即】失效，历史分发记录保留。`)) return;
    try {
      await api.del(`/api/minutes/${id}/recipients/${username}`);
      await load();
    } catch (e) {
      error = e.message;
    }
  }

  function reviewDownload(a) {
    return authedDownload(`/api/minutes/attachments/${a.id}/review`, a.filename).catch((e) => (error = e.message));
  }

  async function grantAgain() {
    if (!addUsername) return;
    error = '';
    try {
      await api.post(`/api/minutes/${id}/recipients/${addUsername}`, {});
      msg = `已为 ${addUsername} 恢复/追加授权并补发提醒邮件。`;
      addUsername = '';
      await load();
    } catch (e) {
      error = e.message;
    }
  }

  $: addableEmployees = employees.filter(
    (e) => e.role === 'STAFF' && e.active
      && e.clearanceLevel >= (minutes ? (minutes.classification === 'PUBLIC' ? 1
        : minutes.classification === 'INTERNAL' ? 2 : minutes.classification === 'SECRET' ? 3 : 4) : 99)
      && !grants.some((g) => g.username === e.username && g.active)
  );
</script>

<h1>审批 / 详情</h1>
{#if error}<div class="error">{error}</div>{/if}
{#if msg}<div class="success">{msg}</div>{/if}

{#if minutes && cls}
  <div class="panel">
    <div class="class-banner" style={`border-color:${cls.color};color:${cls.color};background:${cls.bg}`}>
      <span class="big">★ {cls.label}</span>
      <span>密级 {cls.level} 级 · {minutes.title} · 状态：{STATUS_LABELS[minutes.status]}</span>
    </div>
    <p class="muted">
      记录员：{minutes.createdByName}；当前发布版本：{minutes.currentVersionNo ? `v${minutes.currentVersionNo}` : '无'}；
      {#if minutes.draftVersionNo}待审草稿：v{minutes.draftVersionNo}{/if}
    </p>
    {#if minutes.rejectionComment}
      <p class="muted">上次驳回意见：{minutes.rejectionComment}</p>
    {/if}
  </div>

  {#if draft}
    <div class="panel">
      <h2 style="margin-top:0">草稿版本内容预览（v{draft.versionNo}）</h2>
      <p class="muted">正文与附件属于同一版本；批准后作为整体一起发布。</p>
      <div style="white-space:pre-wrap;line-height:1.7;font-size:14px;background:#f8fafc;border:1px solid var(--border);border-radius:8px;padding:14px;max-height:300px;overflow:auto">{draft.body}</div>
      <h2>附件（{draft.attachments.length}）</h2>
      <table>
        {#each draft.attachments as a}
          <tr>
            <td>{a.filename}</td>
            <td class="muted">{a.contentType} · {(a.size / 1024).toFixed(1)} KB · 版本 v{a.versionNo}</td>
            <td><button on:click={() => reviewDownload(a)}>审查下载</button></td>
          </tr>
        {/each}
      </table>
    </div>
  {/if}

  {#if minutes.status === 'PENDING'}
    <div class="panel">
      <h2 style="margin-top:0">最终分发对象预览与确认</h2>
      <p class="muted">
        以下为记录员建议的候选名单；您可增删后批准。系统按员工【当前】密级资质实时标注：
        资质不足者不可加入最终名单。批准后仅勾选员工收到定向提醒链接。
      </p>
      <div class="pill-list">
        {#each candidates as c}
          <span class="check-pill {finalSet.has(c.username) ? 'in' : 'out'}"
                on:click={() => c.eligible && toggle(c.username)}
                style={c.eligible ? '' : 'color:#b91c1c;border-color:#fecaca'}>
            <input type="checkbox" checked={finalSet.has(c.username)} disabled={!c.eligible} readonly />
            {c.name}（{c.username} · {c.department}/{c.position} ·
            <span class={c.eligible ? 'tag-yes' : 'tag-no'}>资质{c.clearanceLevel}级</span>）
          </span>
        {/each}
      </div>
      <h2>最终名单（{finalSet.size} 人）</h2>
      <div class="pill-list">
        {#each [...finalSet] as u}
          <span class="check-pill in">{u}</span>
        {/each}
      </div>
      <div class="row" style="margin-top:16px">
        <button class="primary" on:click={approve} disabled={busy}>批准并定向发布（{finalSet.size} 人）</button>
        <button class="danger" on:click={() => (showReject = !showReject)} disabled={busy}>驳回</button>
      </div>
      {#if showReject}
        <div style="margin-top:10px">
          <label>驳回意见</label>
          <input bind:value={rejectComment} placeholder="如：接收名单需调整" />
          <div style="margin-top:8px"><button class="danger" on:click={reject}>确认驳回</button></div>
        </div>
      {/if}
    </div>
  {/if}

  <div class="panel">
    <h2 style="margin-top:0">授权名单（历史保留）</h2>
    {#if minutes.status === 'APPROVED'}
      <p class="muted">已发布纪要可在此追加/恢复接收人（如员工调回原岗位），确认后补发提醒邮件并新增分发记录。</p>
      <div class="row" style="margin-bottom:12px">
        <select bind:value={addUsername} style="max-width:340px">
          <option value="">选择员工追加/恢复…</option>
          {#each addableEmployees as e}
            <option value={e.username}>{e.name}（{e.username} · {e.department}/{e.position} · 资质{e.clearanceLevel}级）</option>
          {/each}
        </select>
        <button class="primary" on:click={grantAgain} disabled={!addUsername}>追加/恢复授权</button>
      </div>
    {/if}
    <table>
      <thead><tr><th>员工</th><th>授权时岗位</th><th>资质</th><th>状态</th><th></th></tr></thead>
      <tbody>
        {#each grants as g}
          <tr>
            <td>{g.name}（{g.username}）</td>
            <td>{g.grantedDepartment}/{g.grantedPosition}</td>
            <td>{g.grantedClearanceLevel}级</td>
            <td>
              {#if g.active}<span class="tag-yes">生效中</span>
              {:else}<span class="tag-no">已失效（{g.revokeReason}）</span>{/if}
            </td>
            <td>
              {#if g.active && minutes.status === 'APPROVED'}
                <button class="danger" on:click={() => revoke(g.username)}>撤回授权</button>
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>

  <div class="panel">
    <h2 style="margin-top:0">历史分发记录（永久保留，不随撤回删除）</h2>
    {#if distributions.length === 0}
      <p class="muted">暂无分发记录。</p>
    {:else}
      <table>
        <thead><tr><th>时间</th><th>版本</th><th>接收人</th><th>邮箱</th></tr></thead>
        <tbody>
          {#each distributions as d}
            <tr>
              <td class="muted">{d.sentAt.replace('T', ' ').slice(0, 16)}</td>
              <td>v{d.versionNo}</td>
              <td>{d.recipient}</td>
              <td>{d.email}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    {/if}
  </div>

  <button on:click={() => go('#/')}>返回列表</button>
{/if}
