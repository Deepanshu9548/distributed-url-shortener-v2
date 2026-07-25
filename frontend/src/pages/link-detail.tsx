import { useParams, useNavigate, Link } from 'react-router-dom';
import { useLink, useDeleteLink, useUpdateLink } from '@/hooks/use-links';
import { useLinkStats } from '@/hooks/use-stats';
import { StatsChart } from '@/components/stats-chart';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import { ArrowLeft, ExternalLink, Copy, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useState, useEffect } from 'react';

export default function LinkDetail() {
  const { code } = useParams<{ code: string }>();
  const navigate = useNavigate();
  
  const { data: link, isLoading, isError } = useLink(code || '');
  const { data: stats } = useLinkStats(code || '');
  const deleteMutation = useDeleteLink();
  const updateMutation = useUpdateLink();

  const [longUrl, setLongUrl] = useState('');
  
  useEffect(() => {
    if (link) {
      setLongUrl(link.longUrl);
    }
  }, [link]);

  useEffect(() => {
    if (isError) {
      toast.error('Link not found or access denied');
      navigate('/dashboard');
    }
  }, [isError, navigate]);

  if (isError) {
    return null;
  }

  if (isLoading || !link) {
    return <div className="container max-w-4xl mx-auto px-4 py-8 space-y-8">
      <Skeleton className="h-10 w-1/3" />
      <div className="grid md:grid-cols-2 gap-6">
        <Skeleton className="h-64" />
        <Skeleton className="h-64" />
      </div>
    </div>;
  }

  const shortUrl = `${window.location.origin}/${link.shortCode}`;

  const handleCopy = () => {
    navigator.clipboard.writeText(shortUrl).then(
      () => toast.success('Copied to clipboard'),
      () => toast.error('Failed to copy')
    );
  };

  const handleDelete = () => {
    if (confirm('Are you sure you want to delete this link?')) {
      deleteMutation.mutate(link.shortCode, {
        onSuccess: () => {
          toast.success('Link deleted');
          navigate('/dashboard');
        },
        onError: () => toast.error('Failed to delete link'),
      });
    }
  };

  const handleUpdate = () => {
    updateMutation.mutate({ code: link.shortCode, longUrl }, {
      onSuccess: () => toast.success('Link updated successfully'),
      onError: (err: any) => toast.error(err.response?.data?.error || 'Failed to update link'),
    });
  };

  return (
    <div className="container max-w-5xl mx-auto px-4 sm:px-8 py-8 space-y-8">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" asChild>
            <Link to="/dashboard">
              <ArrowLeft className="h-5 w-5" />
              <span className="sr-only">Back to Dashboard</span>
            </Link>
          </Button>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">{shortUrl}</h1>
            <p className="text-muted-foreground flex items-center gap-2 mt-1">
              Created {new Intl.DateTimeFormat('default', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(link.createdAt))}
            </p>
          </div>
        </div>
        <div className="flex space-x-2">
          <Button variant="outline" onClick={handleCopy}>
            <Copy className="h-4 w-4 mr-2" /> Copy
          </Button>
          <Button variant="destructive" onClick={handleDelete} disabled={deleteMutation.isPending}>
            <Trash2 className="h-4 w-4 mr-2" /> Delete
          </Button>
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle>Link Settings</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>Short Code</Label>
              <Input value={link.shortCode} disabled />
            </div>
            <div className="space-y-2">
              <Label>Destination URL</Label>
              <div className="flex space-x-2">
                <Input 
                  value={longUrl} 
                  onChange={(e) => setLongUrl(e.target.value)}
                />
                <Button variant="outline" size="icon" asChild>
                  <a href={link.longUrl} target="_blank" rel="noreferrer">
                    <ExternalLink className="h-4 w-4" />
                  </a>
                </Button>
              </div>
            </div>
            {link.expiresAt && (
              <div className="space-y-2">
                <Label>Expires At</Label>
                <Input value={new Intl.DateTimeFormat('default', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(link.expiresAt))} disabled />
              </div>
            )}
            <Button 
              onClick={handleUpdate} 
              disabled={longUrl === link.longUrl || updateMutation.isPending}
            >
              {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
            </Button>
          </CardContent>
        </Card>

        {stats && <StatsChart stats={stats} />}
      </div>
    </div>
  );
}
