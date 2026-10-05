<script>
  import { onMount } from 'svelte';
  import { api, getAccount, setAccount } from '../api.js';
  import { refreshMe, refreshUsers } from '../stores.js';

  let account = getAccount() || 'wangwei';
  let users = [];
  let error = '';
  let loading = false;

  onMount(async () => {
    // 登录页无需身份即可读取候选账号名册（仅用于演示切换）
    try { users = await api.get('/api/users'); } catch (e) { error = e.message; }
  });

  async function login() {
    loading = true; error = '';
    setAccount(account);
    const u = await refreshMe();
    if (!u) { error = '账号不存在或已停用'; setAccount(''); }
    else { await refreshUsers(); location.hash = '#/links'; }
    loading = false;
  }
</script>

<div class="login-wrap">
  <div class="card">
    <h2>🔒 登录会议纪要保密分发系统</h2>
    <div class="sub">演示系统：选择一个员工身份登录。每次访问都按该员工<strong>当前岗位密级</strong>与授权实时核验。</div>
    {#if error}<div class="error">{error}</div>{/if}
    <label>选择身份</label>
    <select bind:value={account}>
      {#each users as u}
        <option value={u.account}>{u.name}（{u.account}）· {u.roleLabel} · {u.post} · 密级{u.clearanceLabel}</option>
      {/each}
    </select>
    <div style="margin-top:18px">
      <button on:click={login} disabled={loading} style="width:100%">登录</button>
    </div>
    <div class="watermark">机密文件 · 定向分发 · 全程审计</div>
  </div>
</div>
