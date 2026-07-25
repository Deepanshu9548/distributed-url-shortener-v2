import { LinkTable } from '@/components/link-table';
import { Button } from '@/components/ui/button';
import { Link } from 'react-router-dom';

export default function Dashboard() {
  return (
    <div className="container max-w-6xl mx-auto px-4 sm:px-8 py-8 space-y-8">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
          <p className="text-muted-foreground mt-1">Manage your shortened links and track their performance.</p>
        </div>
        <Button asChild>
          <Link to="/links/new">Shorten New URL</Link>
        </Button>
      </div>
      <LinkTable />
    </div>
  );
}
