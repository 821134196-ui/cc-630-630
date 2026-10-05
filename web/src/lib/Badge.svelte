<script>
  export let level;
  import { classificationOf } from '../lib/constants.js';
  $: c = classificationOf(level);
</script>

<span class="badge" style={`color:${c.color};background:${c.bg}`}>
  ★ {c.label}（{c.level}级）
</span>
