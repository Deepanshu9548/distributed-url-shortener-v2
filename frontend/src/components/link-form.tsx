import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { linkSchema, type LinkFormData } from '@/lib/validators';
import { useCreateLink } from '@/hooks/use-links';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { toast } from 'sonner';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';

export function LinkForm() {
  const createLink = useCreateLink();
  const navigate = useNavigate();
  const [idempotencyKey] = useState(() => crypto.randomUUID());

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LinkFormData>({
    resolver: zodResolver(linkSchema),
    defaultValues: {
      ttlType: 'none',
    },
  });

  const onSubmit = (data: LinkFormData) => {
    createLink.mutate(
      {
        longUrl: data.longUrl,
        customAlias: data.customAlias || undefined,
        ttlSeconds: data.ttlType === 'ttl' && data.ttlSeconds ? Number(data.ttlSeconds) : undefined,
        expiresAt: data.ttlType === 'expiresAt' && data.expiresAt ? new Date(data.expiresAt).toISOString() : undefined,
        idempotencyKey,
      },
      {
        onSuccess: (result) => {
          toast.success('Link created successfully!');
          navigate(`/links/${result.shortCode}`);
        },
        onError: (error: any) => {
          toast.error(error.response?.data?.error || 'Failed to create link');
        },
      }
    );
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      <div className="space-y-2">
        <Label htmlFor="longUrl">Destination URL</Label>
        <Input
          id="longUrl"
          type="url"
          placeholder="https://example.com/very/long/path"
          {...register('longUrl')}
        />
        {errors.longUrl && <p className="text-sm text-destructive">{errors.longUrl.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="customAlias">Custom Alias (Optional)</Label>
        <Input
          id="customAlias"
          placeholder="my-campaign"
          {...register('customAlias')}
        />
        {errors.customAlias && <p className="text-sm text-destructive">{errors.customAlias.message}</p>}
      </div>

      <div className="space-y-2">
        <Label>Expiration (Optional)</Label>
        <Tabs defaultValue="none" onValueChange={(val) => setValue('ttlType', val as any)}>
          <TabsList className="grid w-full grid-cols-3">
            <TabsTrigger value="none">Never</TabsTrigger>
            <TabsTrigger value="ttl">Time to Live</TabsTrigger>
            <TabsTrigger value="expiresAt">Exact Date</TabsTrigger>
          </TabsList>
          
          <TabsContent value="none" className="text-sm text-muted-foreground p-2">
            Link will not expire automatically.
          </TabsContent>
          
          <TabsContent value="ttl" className="space-y-2 mt-4">
            <Label htmlFor="ttlSeconds">Seconds to live</Label>
            <Input
              id="ttlSeconds"
              type="number"
              placeholder="86400 (24 hours)"
              {...register('ttlSeconds', { valueAsNumber: true })}
            />
            {errors.ttlSeconds && <p className="text-sm text-destructive">{errors.ttlSeconds.message}</p>}
          </TabsContent>
          
          <TabsContent value="expiresAt" className="space-y-2 mt-4">
            <Label htmlFor="expiresAt">Expiration Date & Time</Label>
            <Input
              id="expiresAt"
              type="datetime-local"
              {...register('expiresAt')}
            />
            {errors.expiresAt && <p className="text-sm text-destructive">{errors.expiresAt.message}</p>}
          </TabsContent>
        </Tabs>
      </div>

      <Button type="submit" className="w-full" disabled={isSubmitting || createLink.isPending}>
        {createLink.isPending ? 'Shortening...' : 'Shorten URL'}
      </Button>
    </form>
  );
}
