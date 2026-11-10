import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MantineProvider } from '@mantine/core';
import '@mantine/core/styles.css';
import { App } from './app/App';
import { cssVariablesResolver, theme } from './app/theme';

const root = document.getElementById('root');
if (!root) throw new Error('缺少桌面应用挂载节点 #root');

createRoot(root).render(
  <StrictMode>
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver} defaultColorScheme="auto">
      <App />
    </MantineProvider>
  </StrictMode>,
);
