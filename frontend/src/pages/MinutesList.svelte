<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { go } from '../stores.js';

  let list = [];
  let newTitle = '';
  let error = '';
  let loading = true;

  onMount(load);
  async function load() {
    loading = true;
    list = await api.get('/api/minutes');
    loading = false;
  }
  async function create() {
    error = '';
    if (!newTitle.trim()) { error = '请输入纪要标题'; return; }
    try {
      const m = await api.post('/api/minutes', { title: newTitle.trim() });
      go('edit/' + m.id);
    } catch (e) { error = e.message; }
  }
</script>

<div class="card">
  <h2>起草新会议纪要</h2>
  <div class="sub">正文与附件在提交修订时<strong>作为同一版本原子发布</strong>，不能只更新其中一部分。</div>
  {#if error}<div class="error">{error}</div>{/if}
  <div class="row">
    <input type="text" placeholder="输入纪要标题，如：Q4 经营分析会议纪要" bind:value={newTitle}
           style="flex:1" on:keydown={(e)=> e.key==='Enter' && create()} />
    <button on:click={create}>创建并标注密级</button>
  </div>
</div>

<div class="card">
  <h2>全部纪要</h2>
  <div class="sub">点击进入编辑/版本管理页，页面顶部会显著显示密级。</div>
  {#if loading}<div class="muted">加载中…</div>{:else if list.length === 0}
    <div class="muted">暂无纪要。</div>
  {/if}
  <table>
    <thead><tr><th>标题</th><th>当前版本</th><th>密级</th><th>状态</th><th></th></tr></thead>
    <tbody>
      {#each list as m}
        <tr>
          <td><strong>{m.title}</strong></td>
          <td>v{m.currentVersionNo || '—'}</td>
          <td>
            {#if m.classificationLevel}
              <span class="badge {m.classification}">{m.classificationLabel}</span>
            {:else}<span class="muted">—</span>{/if}
          </td>
          <td>{#if m.latestState}<span class="state {m.latestState}">{m.latestStateLabel}</span>{:else}—{/if}</td>
          <td><button class="secondary" on:click={()=>go('edit/' + m.id)}>打开</button></td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
