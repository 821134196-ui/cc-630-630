<script>
  import { onMount } from 'svelte';
  import { me, restoreSession, logout } from './lib/auth.js';
  import { ROLE_LABELS } from './lib/constants.js';
  import Login from './pages/Login.svelte';
  import MinutesList from './pages/MinutesList.svelte';
  import Editor from './pages/Editor.svelte';
  import Approval from './pages/Approval.svelte';
  import ViewMinutes from './pages/ViewMinutes.svelte';
  import Employees from './pages/Employees.svelte';
  import Monitor from './pages/Monitor.svelte';

  let route = { name: 'list', params: {} };
  let ready = false;

  function parseHash() {
    const hash = location.hash.replace(/^#/, '') || '/';
    const [path, query = ''] = hash.split('?');
    const parts = path.split('/').filter(Boolean);
    const q = Object.fromEntries(new URLSearchParams(query));
    if (parts[0] === 'view' && parts[1]) {
      route = { name: 'view', params: { id: parts[1], token: q.t || '' } };
    } else if (parts[0] === 'new') {
      route = { name: 'new' };
    } else if (parts[0] === 'edit' && parts[1]) {
      route = { name: 'edit', params: { id: parts[1] } };
    } else if (parts[0] === 'approve' && parts[1]) {
      route = { name: 'approve', params: { id: parts[1] } };
    } else if (parts[0] === 'employees') {
      route = { name: 'employees' };
    } else if (parts[0] === 'monitor') {
      route = { name: 'monitor' };
    } else {
      route = { name: 'list' };
    }
    route = route;
  }

  onMount(async () => {
    await restoreSession();
    parseHash();
    ready = true;
    window.addEventListener('hashchange', parseHash);
  });

  function go(path) {
    location.hash = path;
  }
</script>

{#if !ready}
  <div class="container"><p class="muted">加载中…</p></div>
{:else if !$me}
  <Login />
{:else}
  <div class="nav">
    <span class="brand">🔒 纪要保密分发</span>
    <a href="#/" class:active={route.name === 'list'}>纪要列表</a>
    {#if $me.role === 'ADMIN'}
      <a href="#/employees" class:active={route.name === 'employees'}>人事调动</a>
    {/if}
    {#if $me.role !== 'STAFF'}
      <a href="#/monitor" class:active={route.name === 'monitor'}>审计 / 邮件外发箱</a>
    {/if}
    <span class="spacer" style="flex:1"></span>
    <span class="muted" style="color:#94a3b8">
      {$me.name}（{ROLE_LABELS[$me.role]} · {$me.department}/{$me.position} · 资质{$me.clearanceLevel}级）
    </span>
    <a on:click={logout}>退出</a>
  </div>

  <div class="container">
    {#if route.name === 'list'}
      <MinutesList {go} />
    {:else if route.name === 'new'}
      <Editor id={null} {go} />
    {:else if route.name === 'edit'}
      <Editor id={route.params.id} {go} />
    {:else if route.name === 'approve'}
      <Approval id={route.params.id} {go} />
    {:else if route.name === 'view'}
      <ViewMinutes id={route.params.id} token={route.params.token} {go} />
    {:else if route.name === 'employees'}
      <Employees />
    {:else if route.name === 'monitor'}
      <Monitor />
    {/if}
  </div>
{/if}
