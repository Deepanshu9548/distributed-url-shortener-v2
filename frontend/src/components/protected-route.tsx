import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '@/lib/auth-store';

export function ProtectedRoute() {
  const { isAuthenticated } = useAuthStore();

  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
