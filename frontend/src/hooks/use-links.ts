import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';

export interface LinkItem {
  shortCode: string;
  shortUrl: string;
  longUrl: string;
  createdAt: string;
  expiresAt: string | null;
}

export interface LinksPage {
  items: LinkItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const useMyLinks = (page: number = 0, size: number = 20) => {
  return useQuery({
    queryKey: ['links', page, size],
    queryFn: async () => {
      const { data } = await api.get<LinksPage>(`/api/me/links?page=${page}&size=${size}`);
      return data;
    },
  });
};

export const useLink = (code: string) => {
  return useQuery({
    queryKey: ['link', code],
    queryFn: async () => {
      const { data } = await api.get<LinkItem>(`/api/links/${code}`);
      return data;
    },
    enabled: !!code,
  });
};

export const useCreateLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: {
      longUrl: string;
      customAlias?: string;
      ttlSeconds?: number;
      expiresAt?: string;
      idempotencyKey: string;
    }) => {
      const { idempotencyKey, ...rest } = payload;
      const { data } = await api.post('/api/links', rest, {
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
      });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['links'] });
    },
  });
};

export const useUpdateLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ code, ...payload }: {
      code: string;
      longUrl?: string;
      ttlSeconds?: number;
      expiresAt?: string;
    }) => {
      const { data } = await api.put(`/api/links/${code}`, payload);
      return data;
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['links'] });
      queryClient.invalidateQueries({ queryKey: ['link', variables.code] });
    },
  });
};

export const useDeleteLink = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (code: string) => {
      await api.delete(`/api/links/${code}`);
    },
    onSuccess: (_, code) => {
      queryClient.invalidateQueries({ queryKey: ['links'] });
      queryClient.invalidateQueries({ queryKey: ['link', code] });
    },
  });
};
