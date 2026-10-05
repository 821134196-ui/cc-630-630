import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// 构建产物直接输出到后端静态资源目录，由 Quarkus 单端口托管，实现“一条命令”
export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../backend/src/main/resources/META-INF/resources',
    emptyOutDir: true
  }
});
