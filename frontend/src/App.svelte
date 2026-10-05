<script>
  import { onMount } from 'svelte';
  import { me, route, refreshMe, refreshUsers, getAccount, setAccount } from './stores.js';
  import Login from './pages/Login.svelte';
  import MinutesList from './pages/MinutesList.svelte';
  import MinutesEditor from './pages/MinutesEditor.svelte';
  import ApprovalList from './pages/ApprovalList.svelte';
  import Preview from './pages/Preview.svelte';
  import MyLinks from './pages/MyLinks.svelte';
  import ViewMinutes from './pages/ViewMinutes.svelte';
  import AdminConsole from './pages/AdminConsole.svelte';

  let currentRoute;
  const unsub = route.subscribe(r => currentRoute = r);

  const HOME_BY_ROLE = {
    SECRETARY: 'minutes',
    APPROVER: 'approvals',
    ADMIN: 'admin',
    EMPLOYEE: 'links'
  };

  onMount(async () => {
    const u = await refreshMe();
    if (u) {
      await refreshUsers();
      if (!location.hash || location.hash === '#' || location.hash === '#/') {
        location.hash = '#/' + (HOME_BY_ROLE[u.role] || 'links');
      }
    }
  });

  function logout() {
    setAccount('');
    location.reload();
  }

  function navClass(...names) {
    return names.includes(currentRoute.name) ? 'active' : '';
  }
</script>

{#if !$me}
  <Login />
{:else}
  <div class="layout">
    <div class="topbar">
      <div class="brand"><span class="lock">🔒</span>会议纪要保密分发系统</div>
      <nav>
        {#if $me.role === 'SECRETARY' || $me.role === 'ADMIN'}
          <a href="#/minutes" class={navClass('minutes', 'edit')}>纪要与起草</a>
        {/if}
        {#if $me.role === 'APPROVER' || $me.role === 'ADMIN'}
          <a href="#/approvals" class={navClass('approvals', 'preview')}>审批中心</a>
        {/if}
        <a href="#/links" class={navClass('links', 'view')}>我的纪要</a>
        {#if $me.role === 'ADMIN'}
          <a href="#/admin" class={navClass('admin')}>管理与审计</a>
        {/if}
      </nav>
      <div class="spacer"></div>
      <div class="me">
        <b>{$me.name}</b>（{$me.roleLabel}·{$me.post}）
        岗位密级：<b>{$me.clearanceLabel}</b>
        &nbsp;<a href="#/links" on:click|preventDefault={logout} style="color:#fca5a5">退出</a>
      </div>
    </div>

    <div class="container">
      {#if currentRoute.name === 'minutes'}
        <MinutesList />
      {:else if currentRoute.name === 'edit'}
        <MinutesEditor minutesId={currentRoute.params[0]} />
      {:else if currentRoute.name === 'approvals'}
        <ApprovalList />
      {:else if currentRoute.name === 'preview'}
        <Preview versionId={currentRoute.params[0]} />
      {:else if currentRoute.name === 'links'}
        <MyLinks />
      {:else if currentRoute.name === 'view'}
        <ViewMinutes token={currentRoute.params[0]} />
      {:else if currentRoute.name === 'admin'}
        <AdminConsole />
      {/if}
    </div>
  </div>
{/if}
