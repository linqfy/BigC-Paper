// The editor server answers only this machine's names for its port, so a page whose domain is rebound to 127.0.0.1
// (DNS rebinding) never gets the publish token nor reaches a write route.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createServer } from './serve.mjs';

function get(port, host, pathname = '/', method = 'GET') {
  return new Promise((resolve, reject) => {
    const request = http.request({ host: '127.0.0.1', port, path: pathname, method, headers: { host, origin: `http://${host}`, 'x-tideframe-token': 'x' } }, response => {
      let body = ''; response.on('data', chunk => { body += chunk; }); response.on('end', () => resolve({ status: response.statusCode, body }));
    });
    request.on('error', reject); request.end();
  });
}

test('a foreign Host is refused before anything is served, the loopback names work', async () => {
  const screens = mkdtempSync(path.join(tmpdir(), 'tf-host-'));
  const server = createServer({ screensDir: screens, token: 'secret-token-value' });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try {
    for (const host of [`evil.example:${port}`, 'localhost:1', `127.0.0.1.evil.example:${port}`]) {
      const page = await get(port, host);
      assert.equal(page.status, 421, host);
      assert.ok(!page.body.includes('secret-token-value'), `${host}: no token`);
      assert.equal((await get(port, host, '/publish', 'POST')).status, 421, `${host}: no publishing`);
    }
    for (const host of [`127.0.0.1:${port}`, `localhost:${port}`]) {
      const page = await get(port, host);
      assert.equal(page.status, 200, host);
      assert.ok(page.body.includes('secret-token-value'), `${host}: the editor gets its token`);
    }
  } finally { server.close(); }
});
