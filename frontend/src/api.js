// 极简 API 封装：登录身份用 localStorage 里的账号，通过 X-Account 头传递（演示系统）。
const ACCOUNT_KEY = 'secmin.account';

export function getAccount() {
  return localStorage.getItem(ACCOUNT_KEY) || '';
}
export function setAccount(a) {
  if (a) localStorage.setItem(ACCOUNT_KEY, a);
  else localStorage.removeItem(ACCOUNT_KEY);
}

async function request(method, path, body, isForm) {
  const headers = {};
  const account = getAccount();
  if (account) headers['X-Account'] = account;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : (isForm ? body : JSON.stringify(body))
  });
  let data = null;
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('application/json')) data = await res.json();
  if (!res.ok) {
    const err = new Error((data && data.message) || `请求失败 (${res.status})`);
    err.status = res.status;
    err.data = data;
    throw err;
  }
  return data;
}

export const api = {
  get: (p) => request('GET', p),
  post: (p, b) => request('POST', p, b, false),
  postForm: (p, form) => request('POST', p, form, true)
};

// 附件下载必须带 X-Account 头，不能用裸 <a href>，否则绕过登录头校验
export async function authedDownload(url, fallbackName) {
  const res = await fetch(url, { headers: { 'X-Account': getAccount() } });
  if (!res.ok) {
    let msg = `下载被拒绝 (${res.status})`;
    try {
      const j = await res.json();
      if (j.message) msg = j.message;
    } catch { /* ignore */ }
    throw new Error(msg);
  }
  const blob = await res.blob();
  const o = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = o;
  a.download = fallbackName || 'attachment';
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(o);
}
