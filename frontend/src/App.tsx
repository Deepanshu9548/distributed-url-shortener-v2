import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { NavBar } from '@/components/nav-bar';
import { ProtectedRoute } from '@/components/protected-route';
import { Toaster } from '@/components/ui/sonner';

import Landing from '@/pages/landing';
import Login from '@/pages/login';
import Register from '@/pages/register';
import Dashboard from '@/pages/dashboard';
import LinkNew from '@/pages/link-new';
import LinkDetail from '@/pages/link-detail';
import NotFound from '@/pages/not-found';

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen flex flex-col bg-background font-sans antialiased">
        <NavBar />
        <main className="flex-1">
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/login" element={<Login />} />
            <Route path="/register" element={<Register />} />
            
            <Route element={<ProtectedRoute />}>
              <Route path="/dashboard" element={<Dashboard />} />
              <Route path="/links/new" element={<LinkNew />} />
              <Route path="/links/:code" element={<LinkDetail />} />
            </Route>

            <Route path="*" element={<NotFound />} />
          </Routes>
        </main>
      </div>
      <footer className="py-6 border-t mt-auto text-center text-sm text-muted-foreground">
        <div className="container mx-auto">
          <p>
            Distributed URL Shortener v2 •{' '}
            <a href="https://github.com/Deepanshu9548/distributed-url-shortener-v2" className="underline hover:text-primary" target="_blank" rel="noreferrer">
              GitHub
            </a>
            {' '}• v{import.meta.env.VITE_APP_VERSION || '1.0.0'}
          </p>
        </div>
      </footer>
      <Toaster />
    </BrowserRouter>
  );
}
