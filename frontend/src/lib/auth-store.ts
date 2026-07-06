import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface AuthState {
  accessToken: string | null;
  setAuth: (token: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      setAuth: (token: string) => set({ accessToken: token }),
      logout: () => {
        localStorage.removeItem('refreshToken');
        set({ accessToken: null });
      },
      isAuthenticated: () => !!get().accessToken,
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({ accessToken: state.accessToken }),
    }
  )
);
