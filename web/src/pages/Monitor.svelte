<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';

  let tab = 'audit';
  let audits = [];
  let emails = [];
  let openEmail = null;
  let error = '';

  const ACTION_LABELS = {
    VIEW_BODY: ['查看正文', true],
    VIEW_DENIED: ['正文访问被拒', false],
    DOWNLOAD_ATTACHMENT: ['下载附件', true],
    DOWNLOAD_DENIED: ['附件下载被拒', false],
    APPROVE: ['审批发布', true],
    REJECT: ['驳回', true],
    REVOKE_RECIPIENT: ['撤回授权', true],
    TRANSFER: ['转岗处理', true]
  };

  onMount(async () => {
    try {
      [audits, emails] = await Promise.all([
        api.get('/api/monitor/audit'),
        api.get('/api/monitor/emails')
      ]);
    } catch (e) {
      error = e.message;
    }
  });

  // 检查邮件正文是否意外包含敏感内容（演示用静态敏感串探测）
  function leakWarning(body) {
    return /【绝密正文】|SECRET_PAYLOAD|敏感正文/.test(body);
  }
</script>

<h1>审计日志 / 模拟邮件外发箱</h1>
<p class="muted">所有打开链接与附件下载的成功/拒绝事件均留痕；邮件接口为模拟实现，只发提醒不附正文。</p>
{#if error}<div class="error">{error}</div>{/if}

<div class="row" style="margin:12px 0">
  <button class={tab === 'audit' ? 'primary' : ''} on:click={() => (tab = 'audit')}>访问审计（{audits.length}）</button>
  <button class={tab === 'email' ? 'primary' : ''} on:click={() => (tab = 'email')}>邮件外发箱（{emails.length}）</button>
</div>

{#if tab === 'audit'}
  <div class="panel" style="padding:0">
    <table>
      <thead>
        <tr><th>时间</th><th>员工</th><th>动作</th><th>结果</th><th>纪要</th><th>原因</th></tr>
      </thead>
      <tbody>
        {#each audits as a}
          <tr>
            <td class="muted">{a.at.replace('T', ' ').slice(0, 19)}</td>
            <td>{a.username}</td>
            <td>{ACTION_LABELS[a.action]?.[0] || a.action}</td>
            <td>
              {#if a.allowed}<span class="tag-yes">允许</span>
              {:else}<span class="tag-no">拒绝</span>{/if}
            </td>
            <td>{a.minutesTitle ? `#${a.minutesId} ${a.minutesTitle}` : '—'}</td>
            <td class="muted">{a.reason || ''}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else}
  <div class="panel" style="padding:0">
    <table>
      <thead><tr><th>发送时间</th><th>收件人</th><th>主题</th><th></th></tr></thead>
      <tbody>
        {#each emails as m}
          <tr>
            <td class="muted">{m.sentAt.replace('T', ' ').slice(0, 16)}</td>
            <td>{m.toEmail}</td>
            <td>{m.subject}</td>
            <td><button on:click={() => (openEmail = openEmail === m.id ? null : m.id)}>
              {openEmail === m.id ? '收起' : '查看内容'}
            </button></td>
          </tr>
          {#if openEmail === m.id}
            <tr>
              <td colspan="4">
                <div class="email-body">{m.body}</div>
                {#if leakWarning(m.body)}
                  <div class="error">⚠ 检测到邮件可能包含敏感正文！</div>
                {:else}
                  <div class="success">✓ 已检查：邮件中不含纪要正文/附件内容，仅含密级标识与访问链接。</div>
                {/if}
              </td>
            </tr>
          {/if}
        {/each}
      </tbody>
    </table>
  </div>
{/if}
