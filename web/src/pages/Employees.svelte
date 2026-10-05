<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { ROLE_LABELS } from '../lib/constants.js';

  let employees = [];
  let selected = null;
  let dept = '';
  let position = '';
  let clearance = 1;
  let error = '';
  let msg = '';

  async function load() {
    employees = await api.get('/api/employees');
  }
  onMount(load);

  function pick(e) {
    selected = e;
    dept = e.department;
    position = e.position;
    clearance = e.clearanceLevel;
    error = '';
    msg = '';
  }

  async function transfer() {
    error = '';
    msg = '';
    if (!selected) return;
    const changed = dept !== selected.department || position !== selected.position;
    const downgrade = clearance < selected.clearanceLevel;
    const tip = changed
      ? '转岗后其原岗位授权将【立即】全部失效，旧链接无法再打开。'
      : downgrade
        ? '密级资质下调后，高于新资质的纪要授权将立即失效。'
        : '确认提交岗位信息变更？';
    if (!confirm(tip)) return;
    try {
      await api.put(`/api/employees/${selected.username}/transfer`, {
        department: dept, position, clearanceLevel: Number(clearance)
      });
      msg = `${selected.name} 的岗位信息已更新，相关旧链接已立即失效（历史分发记录保留）。`;
      await load();
      const fresh = employees.find((x) => x.username === selected.username);
      selected = fresh;
    } catch (e) {
      error = e.message;
    }
  }
</script>

<h1>人事调动 / 密级资质调整</h1>
<p class="muted">转岗或资质下调即时生效：员工无需重新登录，下一次打开旧链接即被拒绝；历史授权与分发记录保留可查。</p>

{#if error}<div class="error">{error}</div>{/if}
{#if msg}<div class="success">{msg}</div>{/if}

<div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
  <div class="panel" style="padding:0">
    <table>
      <thead><tr><th>员工</th><th>岗位</th><th>资质</th></tr></thead>
      <tbody>
        {#each employees as e}
          <tr on:click={() => pick(e)}
              style="cursor:pointer;{selected?.username === e.username ? 'background:#eff6ff' : ''}">
            <td><strong>{e.name}</strong><br /><span class="muted">{e.username} · {ROLE_LABELS[e.role]}</span></td>
            <td>{e.department}<br /><span class="muted">{e.position}</span></td>
            <td>{e.clearanceLevel}级</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>

  <div class="panel">
    {#if selected}
      <h2 style="margin-top:0">调整：{selected.name}</h2>
      <label>部门</label>
      <input bind:value={dept} />
      <label>岗位</label>
      <input bind:value={position} />
      <label>密级资质（1-4）</label>
      <select bind:value={clearance}>
        <option value={1}>1 级（公开）</option>
        <option value={2}>2 级（内部）</option>
        <option value={3}>3 级（机密）</option>
        <option value={4}>4 级（绝密）</option>
      </select>
      <div style="margin-top:16px">
        <button class="primary" on:click={transfer}>应用调动 / 调整</button>
      </div>
      <p class="muted" style="margin-top:14px">
        实时核验机制：访问资格在每次请求时根据此页面上的【当前】值计算，不依赖令牌过期。
      </p>
    {:else}
      <p class="muted">← 请选择一名员工。</p>
    {/if}
  </div>
</div>
