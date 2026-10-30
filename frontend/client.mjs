export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export function normalizeApiUrl(value) {
  const url = new URL(value);
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && local)) {
    throw new Error('Use HTTPS for remote APIs. HTTP is allowed only on localhost.');
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error('Enter only the API base URL, without credentials or query parameters.');
  }
  return url.href.replace(/\/+$/, '');
}

export function createClient(baseUrl, username, password, fetcher = fetch) {
  const base = normalizeApiUrl(baseUrl);
  if (!['admin', 'viewer'].includes(username) || !password) {
    throw new Error('Enter a valid username and password.');
  }
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  const basic = btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''));

  async function request(path, options = {}) {
    const headers = new Headers(options.headers);
    headers.set('Authorization', `Basic ${basic}`);
    if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    let response;
    try {
      response = await fetcher(`${base}${path}`, { ...options, headers, cache: 'no-store' });
    } catch {
      throw new ApiError(0, 'Could not reach the API. Check the URL and confirm the server is running.');
    }
    if (!response.ok) {
      const message = response.status === 401 ? 'Invalid credentials.'
        : response.status === 403 ? 'This account does not have permission for this operation.'
        : response.status === 400 ? 'Invalid data or file. Check the format and try again.'
        : `The API returned error ${response.status}.`;
      throw new ApiError(response.status, message);
    }
    return response.status === 204 ? null : response.json();
  }

  return {
    username,
    listEntries: () => request('/lancamentos'),
    createEntry: entry => request('/lancamentos', { method: 'POST', body: JSON.stringify(entry) }),
    deleteEntry: id => request(`/lancamentos/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    listHistory: () => request('/conciliacao/historico'),
    importStatement: file => {
      const body = new FormData();
      body.append('arquivo', file);
      return request('/conciliacao/importar', { method: 'POST', body });
    }
  };
}
