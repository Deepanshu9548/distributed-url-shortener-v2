import { Button } from '@/components/ui/button';
import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[calc(100vh-4rem)] text-center px-4 space-y-6">
      <h1 className="text-6xl font-extrabold text-primary">404</h1>
      <h2 className="text-2xl font-bold tracking-tight">Page not found</h2>
      <p className="text-muted-foreground max-w-md">
        Sorry, we couldn't find the page you're looking for. It might have been moved or deleted.
      </p>
      <Button asChild>
        <Link to="/">Go back home</Link>
      </Button>
    </div>
  );
}
