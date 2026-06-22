import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AdminShell } from './routes/AdminShell';

createRoot(document.getElementById('root')!).render(
    <StrictMode>
        <AdminShell />
    </StrictMode>,
);
