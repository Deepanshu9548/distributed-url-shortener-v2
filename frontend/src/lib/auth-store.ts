import { create } from 'zustand';

interface AuthState {
  accessToken: string | null;
  setAuth: (token: string) => void;
  logout: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  setAuth: (token: string) => set({ accessToken: token }),
  logout: () => {
    localStorage.removeItem('refreshToken');
    set({ accessToken: null });
    // Note: We don't want to couple Zustand directly to React Router's useNavigate here
    // Redirect should happen at the UI layer or via window.location if necessary.
  },
  isAuthenticated: () => !!get().accessToken,
}));
