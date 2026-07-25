import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Link } from 'react-router-dom';
import { Link as LinkIcon, ArrowRight, Copy } from 'lucide-react';
import { useAuthStore } from '@/lib/auth-store';
import { toast } from 'sonner';
import { api } from '@/lib/api';

const publicSchema = z.object({
  longUrl: z.string().url('Invalid URL').max(8192),
});

export default function Landing() {
  const { isAuthenticated } = useAuthStore();
  const [recentLinks, setRecentLinks] = useState<{ shortCode: string; longUrl: string }[]>([]);

  useEffect(() => {
    try {
      const stored = localStorage.getItem('recentLinks');
      if (stored) {
        setRecentLinks(JSON.parse(stored));
      }
    } catch {
      // Ignore
    }
  }, []);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<{ longUrl: string }>({
    resolver: zodResolver(publicSchema),
  });

  const onSubmit = async (data: { longUrl: string }) => {
    try {
      const res = await api.post('/api/links', {
        longUrl: data.longUrl,
      }, {
        headers: { 'Idempotency-Key': crypto.randomUUID() }
      });
      
      const newLink = { shortCode: res.data.shortCode, longUrl: data.longUrl };
      const updatedLinks = [newLink, ...recentLinks].slice(0, 3);
      setRecentLinks(updatedLinks);
      localStorage.setItem('recentLinks', JSON.stringify(updatedLinks));
      
      toast.success('Link shortened!');
    } catch (err: any) {
      toast.error(err.response?.data?.error || 'Failed to shorten link');
    }
  };

  const handleCopy = (code: string) => {
    const shortUrl = `${window.location.origin}/${code}`;
    navigator.clipboard.writeText(shortUrl).then(
      () => toast.success('Copied!'),
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
          A lightning-fast, highly reliable URL shortener. 
          Manage your links, track your clicks, and optimize your online presence.
        </p>

        <Card className="w-full max-w-2xl mx-auto shadow-lg border-primary/20">
          <CardContent className="p-2 sm:p-4">
            <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col sm:flex-row gap-2">
              <Input
                type="url"
                placeholder="Paste your long link here..."
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
            <Link to="/register" className="text-primary hover:underline font-medium">Sign up</Link> for free to create custom aliases and track clicks.
          </p>
        )}

        {recentLinks.length > 0 && (
          <div className="pt-12">
            <h3 className="text-lg font-semibold mb-4 text-left">Your Recent Links</h3>
            <div className="space-y-3">
              {recentLinks.map((link, idx) => (
                <div key={idx} className="flex items-center justify-between p-4 rounded-lg border bg-card text-card-foreground">
                  <div className="flex flex-col text-left overflow-hidden mr-4">
                    <span className="font-medium text-primary flex items-center gap-2">
                      <LinkIcon className="h-4 w-4" />
                      {window.location.host}/{link.shortCode}
                    </span>
                    <span className="text-sm text-muted-foreground truncate" title={link.longUrl}>{link.longUrl}</span>
                  </div>
                  <Button variant="ghost" size="icon" onClick={() => handleCopy(link.shortCode)}>
                    <Copy className="h-4 w-4" />
                  </Button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
