<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { users, refreshUsers } from '../stores.js';

  const LEVELS = [
    { level: 1, label: '公开' }, { level: 2, label: '内部' },
    { level: 3, label: '秘密' }, { level: 4, label: '机密' }
  ];

  let tab = 'staff';
  let grants = [];
  let audits = [];
  let mails = [];
  let error = '';
  let msg = '';
  let busyId = null;

  // 转岗表单
  let transferPost = {};
  let transferLevel = {};

  onMount(loadAll);

  async function loadAll() {
    await refreshUsers();
    $users.forEach(u => {
      transferPost[u.id] = u.post;
      transferLevel[u.id] = u.clearanceLevel;
    });
    await Promise.all([loadGrants(), loadAudit(), loadMails()]);
  }
  const loadGrants = async () => grants = await api.get('/api/admin/grants');
  const loadAudit = async () => audits = await api.get('/api/admin/audit');
  const loadMails = async () => mails = await api.get('/api/admin/mails');

  async function transfer(u) {
    error = ''; msg = '';
    const post = (transferPost[u.id] || '').trim();
    if (!post) { error = `${u.name} 的新岗位不能为空`; return; }
    if (!confirm(`确认将 ${u.name} 转岗为「${post}」，岗位密级调整为「${LEVELS.find(l=>l.level===transferLevel[u.id]).label}」？\n转岗后，其密级不再满足的旧链接将立即失效。`)) return;
    try {
      await api.post(`/api/users/${u.id}/transfer`, {
        post, clearanceLevel: transferLevel[u.id]
      });
      msg = `${u.name} 已转岗；相关旧链接的访问权限已按新岗位实时重算。`;
      await loadAll();
    } catch (e) { error = e.message; }
  }

  async function revoke(g) {
    error = ''; msg = '';
    if (!confirm(`确认撤销 ${g.userName} 对《${g.minutesTitle}》v${g.versionNo} 的定向授权？\n撤销后其旧链接立即失效，历史记录保留。`)) return;
    busyId = g.id;
    try {
      await api.post(`/api/admin/grants/${g.id}/revoke`, {});
      msg = `已撤销 ${g.userName} 的授权，旧链接立即失效。`;
      await Promise.all([loadGrants(), loadAudit()]);
    } catch (e) { error = e.message; }
    busyId = null;
  }
</script>

<div class="card">
  <h2>管理与审计</h2>
  <div class="sub">转岗与授权撤销即时生效——员工下一次打开旧链接即被拒绝；所有分发记录、访问与拒绝审计永久保留。</div>
  {#if error}<div class="error">{error}</div>{/if}
  {#if msg}<div class="ok-msg">{msg}</div>{/if}
  <div class="tabs">
    <button class="secondary" class:ghost={tab!=='staff'} on:click={()=>tab='staff'}>员工岗位 / 转岗</button>
    <button class="secondary" class:ghost={tab!=='grants'} on:click={()=>tab='grants'}>分发记录与撤销</button>
    <button class="secondary" class:ghost={tab!=='audit'} on:click={()=>tab='audit'}>访问审计</button>
    <button class="secondary" class:ghost={tab!=='mails'} on:click={()=>tab='mails'}>模拟邮件</button>
  </div>

  {#if tab === 'staff'}
    <table>
      <thead><tr><th>员工</th><th>角色</th><th>新岗位</th><th>新岗位密级</th><th></th></tr></thead>
      <tbody>
        {#each $users.filter(u=>u.role==='EMPLOYEE' || u.role==='SECRETARY') as u}
          <tr>
            <td><strong>{u.name}</strong><div class="muted">{u.account} · {u.email}</div></td>
            <td>{u.roleLabel}</td>
            <td><input type="text" bind:value={transferPost[u.id]} /></td>
            <td>
              <select bind:value={transferLevel[u.id]}>
                {#each LEVELS as l}<option value={l.level}>{l.label}（{l.level}）</option>{/each}
              </select>
            </td>
            <td><button on:click={()=>transfer(u)}>转岗并即时生效</button></td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}

  {#if tab === 'grants'}
    <table>
      <thead><tr><th>纪要/版本</th><th>接收人</th><th>分发时间</th><th>版本状态</th><th>授权</th><th>实时可访问</th><th></th></tr></thead>
      <tbody>
        {#each grants as g}
          <tr>
            <td><strong>{g.minutesTitle}</strong><div class="muted">v{g.versionNo}</div></td>
            <td>{g.userName}<div class="muted">{g.userAccount}</div></td>
            <td class="muted">{g.createdAt.slice(0,19).replace('T',' ')}</td>
            <td><span class="state {g.versionState}">{g.versionStateLabel}</span></td>
            <td>
              {#if g.revoked}<span class="state WITHDRAWN">已撤销</span>
              {:else}<span class="state APPROVED">有效</span>{/if}
            </td>
            <td>
              {#if g.currentlyAccessible}<span style="color:var(--ok)">● 可访问</span>
              {:else}<span style="color:var(--danger)">● 已失效</span>{/if}
            </td>
            <td>
              {#if !g.revoked}
                <button class="danger" disabled={busyId===g.id} on:click={()=>revoke(g)}>撤销授权</button>
              {:else}<span class="muted">记录保留</span>{/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}

  {#if tab === 'audit'}
    <div class="muted" style="margin-bottom:8px">共 {audits.length} 条（最近 500 条），放行与拒绝均记录。</div>
    <table>
      <thead><tr><th>时间</th><th>人员</th><th>动作</th><th>结果</th><th>纪要/版本</th><th>原因</th></tr></thead>
      <tbody>
        {#each audits as a}
          <tr>
            <td class="muted">{a.at.slice(0,19).replace('T',' ')}</td>
            <td>{a.userAccount || '—'}</td>
            <td>{a.actionLabel}{#if a.attachmentId}<div class="muted">附件 #{a.attachmentId}</div>{/if}</td>
            <td>
              {#if a.result === 'GRANTED'}<span style="color:var(--ok)">放行</span>
              {:else}<span style="color:var(--danger);font-weight:700">拒绝</span>{/if}
            </td>
            <td class="muted">{a.minutesId ?? ''}/{a.versionId ?? ''}</td>
            <td class="muted">{a.reason || ''}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}

  {#if tab === 'mails'}
    <div class="sub">模拟邮件落库并写入 <code>backend/data/mail/</code>。系统自动校验：<strong>邮件正文不得包含纪要正文</strong>（containsBody 必须为 false）。</div>
    {#each mails as m}
      <div class="v" style="border:1px solid var(--line);border-radius:8px;padding:12px 14px;margin-bottom:10px">
        <div class="row">
          <strong>{m.subject}</strong>
          <span style="flex:1"></span>
          {#if m.containsBody}
            <span class="state WITHDRAWN">⚠ 检测到正文泄漏</span>
          {:else}
            <span class="state APPROVED">✓ 不含正文</span>
          {/if}
        </div>
        <div class="muted" style="margin:4px 0">发往 {m.toEmail} · {m.sentAt.slice(0,19).replace('T',' ')}</div>
        <pre class="body" style="max-height:180px;overflow:auto">{m.bodyText}</pre>
      </div>
    {/each}
  {/if}
</div>
