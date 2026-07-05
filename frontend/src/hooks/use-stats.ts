import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';

export interface LinkStats {
  shortCode: string;
  clickCount: number;
  lastClickAt: string | null;
}

export const useLinkStats = (code: string) => {
  return useQuery({
    queryKey: ['stats', code],
    queryFn: async () => {
      const { data } = await api.get<LinkStats>(`/api/links/${code}/stats`);
      return data;
    },
    enabled: !!code,
  });
};
