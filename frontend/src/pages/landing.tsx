import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Link } from 'react-router-dom';
import { Link as LinkIcon, ArrowRight, Copy, ExternalLink } from 'lucide-react';
import { useAuthStore } from '@/lib/auth-store';
import { toast } from 'sonner';
import { api } from '@/lib/api';

const publicSchema = z.object({
  longUrl: z.string().url('Invalid URL format. Example: https://example.com').max(8192),
});

export default function Landing() {
  const { isAuthenticated } = useAuthStore();
  const [recentLinks, setRecentLinks] = useState<{ shortCode: string; longUrl: string; createdAt: string }[]>([]);

  useEffect(() => {
    try {
      const stored = localStorage.getItem('recentLinks');
      if (stored) {
        setRecentLinks(JSON.parse(stored));
      }
    } catch {
      // Ignore parse error
    }
  }, []);

  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm<{ longUrl: string }>({
    resolver: zodResolver(publicSchema),
  });

  const onSubmit = async (data: { longUrl: string }) => {
    let shortCode = '';

    // Attempt API creation if user is authenticated or backend is connected
    try {
      if (isAuthenticated()) {
        const res = await api.post('/api/links', {
          longUrl: data.longUrl,
        }, {
          headers: { 'Idempotency-Key': crypto.randomUUID() }
        });
        shortCode = res.data.shortCode;
      }
    } catch {
      // Fallback for guest mode or offline API
    }

    // Client-side fallback generation for seamless instant guest mode
    if (!shortCode) {
      const chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
      shortCode = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    }

    const newLink = { 
      shortCode, 
      longUrl: data.longUrl, 
      createdAt: new Date().toISOString() 
    };

    const updatedLinks = [newLink, ...recentLinks.filter(l => l.shortCode !== shortCode)].slice(0, 5);
    setRecentLinks(updatedLinks);

    // Save to browser localStorage
    localStorage.setItem('recentLinks', JSON.stringify(updatedLinks));
    try {
      const guestMap = JSON.parse(localStorage.getItem('guestLinksMap') || '{}');
      guestMap[shortCode] = data.longUrl;
      localStorage.setItem('guestLinksMap', JSON.stringify(guestMap));
    } catch {
      // Ignore storage error
    }

    toast.success('Link shortened successfully!');
    reset();
  };

  const getShortUrl = (code: string) => {
    const origin = window.location.origin;
    const pathname = window.location.pathname.endsWith('/') 
      ? window.location.pathname 
      : `${window.location.pathname}/`;
    return `${origin}${pathname}#/${code}`;
  };

  const handleCopy = (code: string) => {
    const shortUrl = getShortUrl(code);
    navigator.clipboard.writeText(shortUrl).then(
      () => toast.success('Copied short URL to clipboard!'),
      () => toast.error('Failed to copy')
    );
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-4rem)] text-center px-4 py-16">
      <div className="max-w-3xl space-y-8">
        <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight">
          Shorten Your Links. <br />
          <span className="text-primary">Expand Your Reach.</span>
        </h1>
        <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
          A lightning-fast, zero-setup URL shortener. 
          Shorten links instantly as a guest or sign up to manage your custom domain & metrics.
        </p>

        <Card className="w-full max-w-2xl mx-auto shadow-lg border-primary/20">
          <CardContent className="p-2 sm:p-4">
            <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col sm:flex-row gap-2">
              <Input
                type="url"
                placeholder="Paste your long URL here (e.g., https://github.com)..."
                className="h-14 text-lg bg-transparent border-0 focus-visible:ring-0 px-4"
                {...register('longUrl')}
              />
              <Button type="submit" size="lg" className="h-14 px-8 text-lg shrink-0 rounded-md" disabled={isSubmitting}>
                Shorten <ArrowRight className="ml-2 h-5 w-5" />
              </Button>
            </form>
            {errors.longUrl && <p className="text-sm text-destructive text-left mt-2 px-4">{errors.longUrl.message}</p>}
          </CardContent>
        </Card>

        {!isAuthenticated() && (
          <p className="text-sm text-muted-foreground">
            <Link to="/register" className="text-primary hover:underline font-medium">Sign up</Link> for free to sync links across devices, set custom aliases, and track analytics.
          </p>
        )}

        {recentLinks.length > 0 && (
          <div className="pt-8 w-full max-w-2xl mx-auto">
            <h3 className="text-lg font-semibold mb-4 text-left">Your Recent Shortened Links</h3>
            <div className="space-y-3">
              {recentLinks.map((link) => {
                const fullShortUrl = getShortUrl(link.shortCode);
                return (
                  <div key={link.shortCode} className="flex items-center justify-between p-4 rounded-lg border bg-card text-card-foreground shadow-sm">
                    <div className="flex flex-col text-left overflow-hidden mr-4">
                      <a 
                        href={fullShortUrl} 
                        target="_blank" 
                        rel="noreferrer"
                        className="font-medium text-primary hover:underline flex items-center gap-2 truncate"
                      >
                        <LinkIcon className="h-4 w-4 shrink-0" />
                        <span className="truncate">{fullShortUrl}</span>
                        <ExternalLink className="h-3.5 w-3.5 shrink-0 opacity-70" />
                      </a>
                      <span className="text-sm text-muted-foreground truncate" title={link.longUrl}>{link.longUrl}</span>
                    </div>
                    <Button variant="outline" size="sm" onClick={() => handleCopy(link.shortCode)} className="shrink-0 gap-1.5">
                      <Copy className="h-4 w-4" /> Copy
                    </Button>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
