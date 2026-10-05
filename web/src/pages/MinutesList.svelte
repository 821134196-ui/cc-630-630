<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { me } from '../lib/auth.js';
  import { STATUS_LABELS } from '../lib/constants.js';
  import Badge from '../lib/Badge.svelte';

  export let go;
  let items = [];
  let links = {};
  let error = '';
  let loading = true;

  async function load() {
    loading = true;
    error = '';
    try {
      items = await api.get('/api/minutes');
      links = {};
      if ($me.role === 'STAFF') {
        await Promise.all(
          items
            .filter((m) => m.status === 'APPROVED')
            .map(async (m) => {
              try {
                links[m.id] = await api.get(`/api/view/${m.id}/link`);
              } catch (_) {
                links[m.id] = null; // 已转岗/撤回 → 无可用链接
              }
            })
        );
        links = links;
      }
    } catch (e) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  onMount(load);
</script>

<div class="row" style="margin-bottom:12px">
  <h1 style="margin:0">纪要列表</h1>
  <span class="spacer" style="flex:1"></span>
  {#if $me.role === 'RECORDER'}
    <button class="primary" on:click={() => go('#/new')}>＋ 新建纪要</button>
  {/if}
  <button on:click={load}>刷新</button>
</div>

{#if error}<div class="error">{error}</div>{/if}
{#if loading}<p class="muted">加载中…</p>{:else if items.length === 0}
  <div class="panel"><p class="muted">暂无纪要。</p></div>
{/if}

<div class="panel" style="padding:0">
  <table>
    <thead>
      <tr>
        <th>标题</th><th>密级</th><th>状态</th>
        <th>当前版本</th><th>负责人</th><th>更新时间</th><th></th>
      </tr>
    </thead>
    <tbody>
      {#each items as m (m.id)}
        <tr>
          <td><strong>{m.title}</strong>
            {#if m.status === 'REJECTED' && m.rejectionComment}
              <div class="muted">驳回意见：{m.rejectionComment}</div>
            {/if}
          </td>
          <td><Badge level={m.classification} /></td>
          <td><span class="status-badge">{STATUS_LABELS[m.status]}</span></td>
          <td>{m.currentVersionNo ? `v${m.currentVersionNo}` : '—'}{#if m.draftVersionNo}<span class="muted">（草稿 v{m.draftVersionNo}）</span>{/if}</td>
          <td>{m.approverName || '—'}</td>
          <td class="muted">{m.updatedAt?.replace('T', ' ').slice(0, 16)}</td>
          <td class="row" style="gap:6px">
            {#if $me.role === 'STAFF' && m.status === 'APPROVED'}
              {#if links[m.id]}
                <a href={links[m.id].url}><button class="primary">打开链接</button></a>
              {:else}
                <span class="tag-no" title="转岗或授权撤回后旧链接立即失效">链接已失效</span>
              {/if}
            {/if}
            {#if $me.role === 'RECORDER' && (m.status === 'DRAFT' || m.status === 'REJECTED')}
              <button on:click={() => go(`#/edit/${m.id}`)}>编辑/提交</button>
            {/if}
            {#if $me.role === 'APPROVER' && m.status === 'PENDING'}
              <button class="primary" on:click={() => go(`#/approve/${m.id}`)}>审批</button>
            {/if}
            {#if $me.role !== 'STAFF'}
              <button on:click={() => go(`#/approve/${m.id}`)}
                disabled={m.status === 'DRAFT'}>详情</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>

{#if $me.role === 'STAFF'}
  <p class="muted">说明：列表中的链接在每次打开时都会按您【当前】的岗位与授权重新核验；
    若您已转岗或授权被撤回，旧链接会立即失效——即使邮箱里仍保留着提醒邮件。</p>
{/if}
