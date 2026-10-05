// 极简 API 封装：登录态用 X-Username 头携带（与后端演示认证对应）
const USERNAME_KEY = 'mm_username';

export function currentUsername() {
  return localStorage.getItem(USERNAME_KEY);
}

export function setUsername(u) {
  if (u) localStorage.setItem(USERNAME_KEY, u);
  else localStorage.removeItem(USERNAME_KEY);
}

async function request(method, url, body, isForm) {
  const opts = { method, headers: {} };
  const u = currentUsername();
  if (u) opts.headers['X-Username'] = u;
  if (body !== undefined) {
    if (isForm) {
      opts.body = body; // FormData，浏览器自动设置 multipart 边界
    } else {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
  }
  const res = await fetch(url, opts);
  if (res.status === 204) return null;
  const contentType = res.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await res.json() : await res.text();
  if (!res.ok) {
    const msg = (data && data.error) || `请求失败 (${res.status})`;
    const err = new Error(msg);
    err.status = res.status;
    throw err;
  }
  return data;
}

export const api = {
  get: (u) => request('GET', u),
  post: (u, b) => request('POST', u, b, false),
  put: (u, b) => request('PUT', u, b, false),
  del: (u) => request('DELETE', u),
  upload: (method, u, formData) => request(method, u, formData, true)
};

/** 附件下载地址（同样需要登录头与令牌，服务端实时核验） */
export function downloadUrl(attachmentId, token) {
  return `/api/attachments/${attachmentId}/download?t=${encodeURIComponent(token)}`;
}

// 带认证头的文件下载（无法用普通 <a>，因为要携带 X-Username）
export async function authedDownload(url, filename) {
  const res = await fetch(url, { headers: { 'X-Username': currentUsername() || '' } });
  if (!res.ok) {
    let msg = `下载被拒绝 (${res.status})`;
    try {
      const j = await res.json();
      if (j.error) msg = j.error;
    } catch (_) {}
    throw new Error(msg);
  }
  const blob = await res.blob();
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}
