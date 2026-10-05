import { writable } from 'svelte/store';
import { api, getAccount, setAccount } from './api.js';

export { getAccount, setAccount };
export const me = writable(null);
export const users = writable([]);

export async function refreshMe() {
  if (!getAccount()) { me.set(null); return null; }
  try {
    const u = await api.get('/api/users/me');
    me.set(u);
    return u;
  } catch {
    setAccount('');
    me.set(null);
    return null;
  }
}

export async function refreshUsers() {
  users.set(await api.get('/api/users'));
}

// 极简 hash 路由
export const route = writable(parseHash());
window.addEventListener('hashchange', () => route.set(parseHash()));

function parseHash() {
  const h = location.hash.replace(/^#\/?/, '');
  const [path, ...rest] = h.split('/');
  return { name: path || 'home', params: rest };
}

export function go(path) {
  location.hash = '#/' + path;
}
