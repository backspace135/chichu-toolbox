import { copyFileSync } from 'node:fs';

copyFileSync(
  new URL('./dist/index.html', import.meta.url),
  new URL('../desktop-mantine-mock.html', import.meta.url),
);
