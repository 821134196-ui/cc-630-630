import { writable } from 'svelte/store';
import { api, setUsername, currentUsername } from './api.js';

export const me = writable(null);

export async function login(username) {
  const user = await api.post('/api/login', { username });
  setUsername(user.username);
  me.set(user);
  return user;
}

export function logout() {
  setUsername(null);
  me.set(null);
}

export async function restoreSession() {
  if (!currentUsername()) {
    me.set(null);
    return null;
  }
  try {
    const user = await api.get('/api/me');
    me.set(user);
    return user;
  } catch (e) {
    setUsername(null);
    me.set(null);
    return null;
  }
}
