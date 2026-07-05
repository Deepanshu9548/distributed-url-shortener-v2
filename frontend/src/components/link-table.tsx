import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMyLinks, useDeleteLink } from '@/hooks/use-links';
import { useLinkStats } from '@/hooks/use-stats';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Copy, Edit2, Trash2, ExternalLink } from 'lucide-react';
import { toast } from 'sonner';

function LinkRow({ item }: { item: any }) {
  const { data: stats } = useLinkStats(item.shortCode);
  const deleteMutation = useDeleteLink();

  const handleCopy = () => {
    const shortUrl = `${window.location.origin}/${item.shortCode}`;
    navigator.clipboard.writeText(shortUrl).then(
      () => toast.success('Copied to clipboard'),
      () => toast.error('Failed to copy')
    );
  };

  const handleDelete = () => {
    if (confirm('Are you sure you want to delete this link?')) {
      deleteMutation.mutate(item.shortCode, {
        onSuccess: () => toast.success('Link deleted'),
        onError: () => toast.error('Failed to delete link'),
      });
    }
  };

  return (
    <TableRow>
      <TableCell className="font-medium">
        <div className="flex items-center gap-2">
          <span>{item.shortCode}</span>
          <Button variant="ghost" size="icon" className="h-6 w-6" onClick={handleCopy}>
            <Copy className="h-3 w-3" />
          </Button>
        </div>
      </TableCell>
      <TableCell className="max-w-[200px] truncate" title={item.longUrl}>
        <a href={item.longUrl} target="_blank" rel="noreferrer" className="flex items-center gap-1 hover:underline text-muted-foreground">
          {item.longUrl}
          <ExternalLink className="h-3 w-3 flex-shrink-0" />
        </a>
      </TableCell>
      <TableCell>
        {new Intl.DateTimeFormat('default', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(item.createdAt))}
      </TableCell>
      <TableCell>
        {stats ? stats.clickCount : <Skeleton className="h-4 w-8" />}
      </TableCell>
      <TableCell className="text-right space-x-2">
        <Button variant="outline" size="sm" asChild>
          <Link to={`/links/${item.shortCode}`}>
            <Edit2 className="h-4 w-4 mr-1" /> Edit
          </Link>
        </Button>
        <Button variant="destructive" size="sm" onClick={handleDelete} disabled={deleteMutation.isPending}>
          <Trash2 className="h-4 w-4" />
        </Button>
      </TableCell>
    </TableRow>
  );
}

export function LinkTable() {
  const [page, setPage] = useState(0);
  const { data, isLoading, isError } = useMyLinks(page, 20);

  if (isLoading) {
    return <div className="space-y-4">
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-20 w-full" />
    </div>;
  }

  if (isError || !data) {
    return <div className="text-center text-destructive">Failed to load links.</div>;
  }

  return (
    <div className="space-y-4">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Short Code</TableHead>
              <TableHead>Long URL</TableHead>
              <TableHead>Created</TableHead>
              <TableHead>Clicks</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.items.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground h-24">
                  No links found. Create one to get started!
                </TableCell>
              </TableRow>
            ) : (
              data.items.map((item) => <LinkRow key={item.shortCode} item={item} />)
            )}
          </TableBody>
        </Table>
      </div>

      {data.totalPages > 1 && (
        <div className="flex items-center justify-end space-x-2 py-4">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(p => Math.max(0, p - 1))}
            disabled={page === 0}
          >
            Previous
          </Button>
          <div className="text-sm text-muted-foreground">
            Page {page + 1} of {data.totalPages}
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
            disabled={page >= data.totalPages - 1}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
