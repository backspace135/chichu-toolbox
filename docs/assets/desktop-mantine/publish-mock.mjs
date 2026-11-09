import { readFileSync, writeFileSync } from 'node:fs';

// 构建产物去除行尾空白，保持 git diff --check 干净。
const html = readFileSync(new URL('./dist/index.html', import.meta.url), 'utf8')
  .replace(/[ \t]+(?=\n)/g, '');

writeFileSync(new URL('./dist/index.html', import.meta.url), html);
writeFileSync(new URL('../desktop-mantine-mock.html', import.meta.url), html);
