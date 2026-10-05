<script>
  import { onMount } from 'svelte';
  import { api } from '../api.js';
  import { go, me } from '../stores.js';

  let links = [];
  let loading = true;
  let error = '';

  onMount(load);
  async function load() {
    loading = true; error = '';
    try { links = await api.get('/api/access/my-links'); }
    catch (e) { error = e.message; }
    loading = false;
  }
</script>

<div class="card">
  <h2>我的纪要链接</h2>
  <div class="sub">
    您收到的每一封提醒邮件都对应一条专属链接。<strong>收到邮件不代表持续拥有访问权</strong>：
    每次打开都按您当前岗位密级与授权状态实时核验；转岗、授权撤销或负责人撤回后，旧链接立即失效。
  </div>
  {#if error}<div class="error">{error}</div>{/if}
  {#if loading}<div class="muted">加载中…</div>
  {:else if links.length === 0}
    <div class="muted">您暂未收到任何定向分发的纪要。</div>
  {/if}
  <table>
    <thead><tr><th>纪要</th><th>版本</th><th>密级</th><th>实时可访问性</th><th>最近打开</th><th></th></tr></thead>
    <tbody>
      {#each links as l}
        <tr>
          <td><strong>{l.minutesTitle}</strong></td>
          <td>v{l.versionNo}</td>
          <td><span class="badge {l.classification}">{l.classificationLabel}</span></td>
          <td>
            {#if l.accessibleNow}
              <span class="state APPROVED">● 现在可访问</span>
            {:else}
              <span class="state WITHDRAWN">● 已失效</span>
              <div class="muted">{l.inaccessibleReason}</div>
            {/if}
          </td>
          <td class="muted">{l.lastAccessAt ? l.lastAccessAt.slice(0,19).replace('T',' ') : '—'}</td>
          <td>
            {#if l.accessibleNow}
              <button on:click={()=>go('view/' + l.token)}>打开</button>
            {:else}
              <button class="secondary" on:click={()=>go('view/' + l.token)}>尝试打开</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
