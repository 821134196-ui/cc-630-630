<script>
  import { login } from '../lib/auth.js';
  let username = '';
  let error = '';
  let busy = false;

  const demoAccounts = [
    ['recorder', '记录员：起草/修订纪要'],
    ['approver', '负责人：审批接收名单、撤回授权'],
    ['admin', '管理员：员工转岗与密级资质调整'],
    ['zhang', '张三 · 研发部高级工程师（3级）'],
    ['wang', '王五 · 研发部工程师（2级）'],
    ['chen', '陈敏 · 市场部专员（2级）'],
    ['liu', '刘洋 · 战略部分析师（4级）']
  ];

  async function submit() {
    error = '';
    busy = true;
    try {
      await login(username.trim());
      location.hash = '/';
    } catch (e) {
      error = e.message;
    } finally {
      busy = false;
    }
  }
</script>

<div class="container" style="max-width:560px;margin-top:60px">
  <div class="panel">
    <h1>🔒 企业会议纪要保密分发系统</h1>
    <p class="muted">演示登录：选择一个演示账号。访问任何链接都会按当前岗位与授权实时核验。</p>
    <form on:submit|preventDefault={submit}>
      <label>用户名</label>
      <input bind:value={username} placeholder="如 zhang" />
      {#if error}<div class="error">{error}</div>{/if}
      <div style="margin-top:14px">
        <button class="primary" disabled={busy} type="submit">{busy ? '登录中…' : '登录'}</button>
      </div>
    </form>
    <h2>演示账号</h2>
    <table>
      {#each demoAccounts as [u, desc]}
        <tr>
          <td style="width:90px"><a href="#" on:click|preventDefault={() => (username = u)}>{u}</a></td>
          <td class="muted">{desc}</td>
        </tr>
      {/each}
    </table>
  </div>
</div>
