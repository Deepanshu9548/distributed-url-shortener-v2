import { useMutation } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { useAuthStore } from '@/lib/auth-store';
import type { LoginFormData, RegisterFormData } from '@/lib/validators';
import { useNavigate } from 'react-router-dom';

export const useLogin = () => {
  const { setAuth } = useAuthStore();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: async (data: LoginFormData) => {
      const response = await api.post('/api/auth/login', data);
      return response.data;
    },
    onSuccess: (data) => {
      setAuth(data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
      navigate('/dashboard');
    },
  });
};

export const useRegister = () => {
  const { setAuth } = useAuthStore();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: async (data: RegisterFormData) => {
      // Create user
      await api.post('/api/auth/register', { email: data.email, password: data.password });
      // Auto login
      const response = await api.post('/api/auth/login', { email: data.email, password: data.password });
      return response.data;
    },
    onSuccess: (data) => {
      setAuth(data.accessToken);
      localStorage.setItem('refreshToken', data.refreshToken);
      navigate('/dashboard');
    },
  });
};

export const useLogout = () => {
  const { logout, accessToken } = useAuthStore();
  const navigate = useNavigate();

  return useMutation({
    mutationFn: async () => {
      const refreshToken = localStorage.getItem('refreshToken');
      if (refreshToken && accessToken) {
        // Best effort logout, ignoring errors
        await api.post('/api/auth/logout', { refreshToken }).catch(() => {});
      }
    },
    onSettled: () => {
      logout();
      navigate('/login');
    },
  });
};
