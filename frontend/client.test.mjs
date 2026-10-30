import test from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, createClient, normalizeApiUrl } from './client.mjs';

test('remote Basic authentication requires HTTPS', () => {
  assert.throws(() => normalizeApiUrl('http://example.com/api'), /HTTPS/);
  assert.equal(normalizeApiUrl('http://localhost:8080/api/'), 'http://localhost:8080/api');
  assert.equal(normalizeApiUrl('https://example.com/api'), 'https://example.com/api');
});

test('entry creation uses the authenticated API and JSON payload', async () => {
  let captured;
  const fetcher = async (url, options) => {
    captured = { url, options };
    return { ok: true, status: 201, json: async () => ({ id: 7 }) };
  };
  const client = createClient('http://localhost:8080/api', 'admin', 'secret', fetcher);
  const result = await client.createEntry({ valor: 1500, tipo: 'DEBITO' });
  assert.deepEqual(result, { id: 7 });
  assert.equal(captured.url, 'http://localhost:8080/api/lancamentos');
  assert.equal(captured.options.method, 'POST');
  assert.equal(captured.options.headers.get('Authorization'), 'Basic YWRtaW46c2VjcmV0');
  assert.equal(captured.options.headers.get('Content-Type'), 'application/json');
  assert.deepEqual(JSON.parse(captured.options.body), { valor: 1500, tipo: 'DEBITO' });
});

test('statement upload sends multipart data without overriding its content type', async () => {
  let captured;
  const fetcher = async (_url, options) => {
    captured = options;
    return { ok: true, status: 200, json: async () => ({ totalConciliados: 2 }) };
  };
  const client = createClient('http://localhost:8080/api', 'admin', 'secret', fetcher);
  const file = new File(['Data,Valor'], 'example.csv', { type: 'text/csv' });
  await client.importStatement(file);
  assert.equal(captured.method, 'POST');
  assert.equal(captured.headers.has('Content-Type'), false);
  assert.equal(captured.body.get('arquivo'), file);
});

test('authentication and authorization failures are distinct', async () => {
  for (const [status, expected] of [[401, 'Invalid credentials'], [403, 'permission']]) {
    const client = createClient('https://example.com/api', 'viewer', 'secret', async () => ({ ok: false, status }));
    await assert.rejects(client.listEntries(), error => error instanceof ApiError && error.status === status && error.message.includes(expected));
  }
});
