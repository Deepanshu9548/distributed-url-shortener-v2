import { LinkForm } from '@/components/link-form';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function LinkNew() {
  return (
    <div className="container max-w-3xl mx-auto px-4 sm:px-8 py-8 space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" asChild>
          <Link to="/dashboard">
            <ArrowLeft className="h-5 w-5" />
            <span className="sr-only">Back to Dashboard</span>
          </Link>
        </Button>
        <h1 className="text-2xl font-bold tracking-tight">Create New Link</h1>
      </div>
      
      <Card>
        <CardHeader>
          <CardTitle>Link Details</CardTitle>
          <CardDescription>Enter the long URL and configure optional settings.</CardDescription>
        </CardHeader>
        <CardContent>
          <LinkForm />
        </CardContent>
      </Card>
    </div>
  );
}
