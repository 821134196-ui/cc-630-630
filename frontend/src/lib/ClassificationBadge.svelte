<script>
  export let level;
  const MAP = {
    1: ['PUBLIC', '公开'],
    2: ['INTERNAL', '内部'],
    3: ['CONFIDENTIAL', '秘密'],
    4: ['SECRET', '机密']
  };
  $: pair = MAP[level] || MAP[1];
</script>
<span class="badge {pair[0]}">{pair[1]}</span>
