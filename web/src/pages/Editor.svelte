<script>
  import { onMount } from 'svelte';
  import { api } from '../lib/api.js';
  import { CLASSIFICATIONS, classificationOf, STATUS_LABELS } from '../lib/constants.js';

  export let id; // null = 新建；数字 = 修订
  export let go;

  const isEdit = id !== null;

  let title = '';
  let classification = 'INTERNAL';
  let body = '';
  let files = [];
  let minutes = null;
  let employees = [];
  let chosen = new Set();
  let error = '';
  let msg = '';
  let busy = false;

  $: cls = classificationOf(classification);

  onMount(async () => {
    try {
      employees = await api.get('/api/employees');
      if (isEdit) {
        minutes = await api.get(`/api/minutes/${id}`);
        title = minutes.title;
        classification = minutes.classification;
        body = ''; // 修订必须重新提交正文+附件（同一版本不可拆分）
        // 预选现有授权中的员工
        try {
          const grants = await api.get(`/api/minutes/${id}/grants`);
          chosen = new Set(grants.filter((g) => g.active).map((g) => g.username));
          chosen = chosen;
        } catch (_) {}
      }
    } catch (e) {
      error = e.message;
    }
  });

  function onFiles(e) {
    files = Array.from(e.target.files || []);
  }

  function toggle(u) {
    if (chosen.has(u)) chosen.delete(u);
    else chosen.add(u);
    chosen = new Set(chosen);
  }

  function buildForm() {
    if (!title.trim()) throw new Error('请填写标题');
    if (!body.trim()) throw new Error('请填写正文');
    if (files.length === 0) {
      throw new Error('本版本必须至少包含一个附件——正文与附件将作为同一版本一起发布，不能只提交其中一部分');
    }
    const fd = new FormData();
    fd.append('title', title);
    fd.append('classification', classification);
    fd.append('body', body);
    for (const f of files) fd.append('files', f);
    return fd;
  }

  async function saveDraft(submit) {
    error = '';
    msg = '';
    busy = true;
    try {
      const fd = buildForm();
      let minutesId = id;
      if (!isEdit) {
        const created = await api.upload('POST', '/api/minutes', fd);
        minutesId = created.id;
      } else {
        await api.upload('PUT', `/api/minutes/${id}/revise`, fd);
      }
      if (submit) {
        if (chosen.size === 0) throw new Error('请至少勾选一位候选接收人');
        await api.post(`/api/minutes/${minutesId}/submit`, { usernames: [...chosen] });
        msg = '已提交审批，等待负责人确认最终接收名单。';
      } else {
        msg = '草稿已保存（正文与附件属于同一版本）。';
      }
      setTimeout(() => go('#/'), 800);
    } catch (e) {
      error = e.message;
    } finally {
      busy = false;
    }
  }
</script>

<h1>{isEdit ? `修订纪要：${minutes?.title || '#' + id}` : '新建会议纪要'}</h1>
<p class="muted">记录员起草：上传正文与附件并标注密级；保存时正文与附件作为【同一个不可拆分的版本】写入。</p>

{#if isEdit && minutes}
  <p class="muted">
    当前发布版本：{minutes.currentVersionNo ? `v${minutes.currentVersionNo}` : '无'}；
    状态：{STATUS_LABELS[minutes.status]}
    {#if minutes.status === 'APPROVED'}——本次修订获批前，旧版本继续有效。{/if}
  </p>
{/if}

{#if error}<div class="error">{error}</div>{/if}
{#if msg}<div class="success">{msg}</div>{/if}

<div class="panel">
  <!-- 密级显著标注：编辑全程置顶展示 -->
  <div class="class-banner" style={`border-color:${cls.color};color:${cls.color};background:${cls.bg}`}>
    <span class="big">★ {cls.label}</span>
    <span>密级 {cls.level} 级 · 仅密级资质 ≥ {cls.level} 级的员工可被列入接收名单</span>
  </div>

  <label>纪要标题</label>
  <input bind:value={title} placeholder="如：2026-Q4 产品战略评审会纪要" />

  <label>密级（请谨慎选择）</label>
  <select bind:value={classification}>
    {#each CLASSIFICATIONS as c}
      <option value={c.key}>{c.label}（{c.level}级）</option>
    {/each}
  </select>

  <label>纪要正文（敏感内容，仅通过受控链接查看；提醒邮件不会包含正文）</label>
  <textarea bind:value={body} placeholder="请输入会议纪要正文…"></textarea>

  <label>附件（{files.length} 个）——与正文同版本提交，发布时不可只更新其中一部分</label>
  <input type="file" multiple on:change={onFiles} />
  {#if files.length > 0}
    <ul class="muted" style="font-size:13px">
      {#each files as f}
        <li>{f.name}（{(f.size / 1024).toFixed(1)} KB）</li>
      {/each}
    </ul>
  {/if}
</div>

<div class="panel">
  <h2 style="margin-top:0">候选接收名单（提交给负责人审批）</h2>
  <p class="muted">密级资质不足者标红，不应勾选；负责人审批时可再次调整并预览最终分发对象。</p>
  <div class="pill-list">
    {#each employees.filter((e) => e.role === 'STAFF' && e.active) as e}
      {@const enough = e.clearanceLevel >= cls.level}
      <span class="check-pill {chosen.has(e.username) ? 'in' : 'out'}"
            class:disabled={!enough}
            on:click={() => enough && toggle(e.username)}
            style={enough ? '' : 'color:#b91c1c;border-color:#fecaca'}>
        <input type="checkbox" checked={chosen.has(e.username)} disabled={!enough} readonly />
        {e.name}（{e.username} · {e.department}/{e.position} ·
        <span class={enough ? 'tag-yes' : 'tag-no'}>资质{e.clearanceLevel}级</span>）
      </span>
    {/each}
  </div>
</div>

<div class="row">
  <button on:click={() => saveDraft(false)} disabled={busy}>保存草稿</button>
  <button class="primary" on:click={() => saveDraft(true)} disabled={busy}>
    {busy ? '处理中…' : '保存并提交审批'}
  </button>
  <span class="spacer" style="flex:1"></span>
  <button on:click={() => go('#/')}>返回</button>
</div>
