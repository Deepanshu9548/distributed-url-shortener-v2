import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '@/lib/api';

export default function RedirectHandler() {
  const { code } = useParams<{ code: string }>();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;

    // 1. Check guest links stored in browser localStorage
    try {
      const guestMap = JSON.parse(localStorage.getItem('guestLinksMap') || '{}');
      if (guestMap[code]) {
        window.location.href = guestMap[code];
        return;
      }
      const recentLinks = JSON.parse(localStorage.getItem('recentLinks') || '[]');
      const found = recentLinks.find((l: any) => l.shortCode === code);
      if (found && found.longUrl) {
        window.location.href = found.longUrl;
        return;
      }
    } catch {
      // Ignore storage parse error
    }

    // 2. Fallback to API check if present
    api.get(`/api/links/${code}`)
      .then((res) => {
        if (res.data && res.data.longUrl) {
          window.location.href = res.data.longUrl;
        } else {
          setError('Short URL not found');
        }
      })
      .catch(() => {
        setError('Short link not found or has expired');
      });
  }, [code]);

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
        <h2 className="text-3xl font-bold mb-4 text-destructive">Link Not Found</h2>
        <p className="text-muted-foreground mb-6">{error}</p>
        <a href="#/" className="px-6 py-3 bg-primary text-primary-foreground rounded-md font-medium hover:opacity-90">
          Return to Shortener
        </a>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary mb-4"></div>
      <p className="text-lg font-medium text-muted-foreground">Redirecting to target URL...</p>
    </div>
  );
}
